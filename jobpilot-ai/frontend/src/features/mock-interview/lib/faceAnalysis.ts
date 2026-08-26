import { FaceLandmarker, FilesetResolver, type FaceLandmarkerResult } from "@mediapipe/tasks-vision";

// 2026-08-04: 여기서도 "표정으로 감정을 판독"하지 않는다 - 눈 깜빡임 빈도, 고개 움직임처럼
// 근거를 바로 설명할 수 있는 객관적 신호만 뽑는다 (음성 쪽 audio_analysis.py와 같은 원칙).
// 얼굴 인식은 서버로 영상을 보내지 않고 브라우저 안에서 전부 처리한다(WASM) - 카메라
// 프레임을 굳이 서버에 스트리밍할 필요가 없고, 개인정보(얼굴 영상)를 안 보내는 게 낫다.

let cachedLandmarker: FaceLandmarker | null = null;
let loadingPromise: Promise<FaceLandmarker> | null = null;

export function loadFaceLandmarker(): Promise<FaceLandmarker> {
  if (cachedLandmarker) return Promise.resolve(cachedLandmarker);
  if (loadingPromise) return loadingPromise;

  loadingPromise = (async () => {
    const vision = await FilesetResolver.forVisionTasks(
      "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@latest/wasm",
    );
    const createLandmarker = (delegate: "GPU" | "CPU") => FaceLandmarker.createFromOptions(vision, {
      baseOptions: {
        modelAssetPath:
          "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
        delegate,
      },
      outputFaceBlendshapes: true,
      outputFacialTransformationMatrixes: false,
      runningMode: "VIDEO",
      numFaces: 1,
    });
    // Some desktop GPU/WebGL drivers reject MediaPipe's delegate while the
    // camera itself works. Keep the visible face guide/landmarks available by
    // falling back to CPU instead of abandoning face analysis.
    let landmarker: FaceLandmarker;
    try {
      landmarker = await createLandmarker("GPU");
    } catch {
      landmarker = await createLandmarker("CPU");
    }
    cachedLandmarker = landmarker;
    return landmarker;
  })().catch((error) => {
    loadingPromise = null;
    throw error;
  });

  return loadingPromise;
}

export interface FaceFrameSample {
  timestampMs: number;
  blinkScore: number; // eyeBlinkLeft/Right 평균 (0~1)
  noseX: number;
  noseY: number;
  gazeRatio: number | null; // 0~1, 0.5=양쪽 눈 구석 사이 정중앙, 홍채 인식 실패 시 null
}

// MediaPipe face mesh 기준 코끝 근처 랜드마크 인덱스.
const NOSE_TIP_INDEX = 1;

// face_landmarker 모델은 기본으로 홍채(iris) 랜드마크까지 포함해 478개 점을 내려준다
// (0~467: 얼굴 메시, 468~472: 오른쪽 눈 홍채(468=중심), 473~477: 왼쪽 눈 홍채(473=중심)).
// 33/133, 362/263은 각 눈의 양쪽 구석(눈꼬리) 점이다.
const RIGHT_EYE_IRIS_INDEX = 468;
const RIGHT_EYE_CORNER_A_INDEX = 33;
const RIGHT_EYE_CORNER_B_INDEX = 133;
const LEFT_EYE_IRIS_INDEX = 473;
const LEFT_EYE_CORNER_A_INDEX = 362;
const LEFT_EYE_CORNER_B_INDEX = 263;

// MediaPipe FaceMesh의 얼굴 윤곽선(턱선+이마) 인덱스를 순서대로 이어놓은 것.
// 478개 점을 다 찍으면 너무 촘촘해 보여서, 이 윤곽선만 선으로 그려 얼굴 위치를
// 보여주는 용도로 쓴다.
export const FACE_OVAL_INDICES = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150,
  136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10,
];

// 눈 구석 두 점 사이에서 홍채가 상대적으로 어디 있는지(0~1, 0.5=가운데)를 계산한다.
// 3D 시선 방향을 추정하는 게 아니라 2D 상대 위치라, 고개를 심하게 돌리면 같이
// 왜곡될 수 있는 근사치다 - 그래도 "카메라를 대체로 정면으로 보고 있는지"의
// 지표로는 코끝 이동량보다 훨씬 직접적이다.
function irisHorizontalRatio(
  landmarks: { x: number; y: number }[],
  irisIndex: number,
  cornerAIndex: number,
  cornerBIndex: number,
): number | null {
  const iris = landmarks[irisIndex];
  const a = landmarks[cornerAIndex];
  const b = landmarks[cornerBIndex];
  if (!iris || !a || !b) return null;
  const left = Math.min(a.x, b.x);
  const right = Math.max(a.x, b.x);
  if (right - left < 1e-6) return null;
  return (iris.x - left) / (right - left);
}

export function sampleFrame(result: FaceLandmarkerResult, timestampMs: number): FaceFrameSample | null {
  const landmarks = result.faceLandmarks?.[0];
  if (!landmarks || !landmarks[NOSE_TIP_INDEX]) return null;

  const blendshapes = result.faceBlendshapes?.[0]?.categories ?? [];
  const left = blendshapes.find((c) => c.categoryName === "eyeBlinkLeft")?.score ?? 0;
  const right = blendshapes.find((c) => c.categoryName === "eyeBlinkRight")?.score ?? 0;

  const rightGaze = irisHorizontalRatio(
    landmarks,
    RIGHT_EYE_IRIS_INDEX,
    RIGHT_EYE_CORNER_A_INDEX,
    RIGHT_EYE_CORNER_B_INDEX,
  );
  const leftGaze = irisHorizontalRatio(landmarks, LEFT_EYE_IRIS_INDEX, LEFT_EYE_CORNER_A_INDEX, LEFT_EYE_CORNER_B_INDEX);
  const gazeSamples = [rightGaze, leftGaze].filter((v): v is number => v !== null);
  const gazeRatio = gazeSamples.length > 0 ? gazeSamples.reduce((sum, v) => sum + v, 0) / gazeSamples.length : null;

  return {
    timestampMs,
    blinkScore: (left + right) / 2,
    noseX: landmarks[NOSE_TIP_INDEX].x,
    noseY: landmarks[NOSE_TIP_INDEX].y,
    gazeRatio,
  };
}

export interface FaceMetrics {
  blinkCount: number;
  blinkRatePerMin: number; // 참고용 환산치 - 화면에는 durationSec/expectedBlinkRange를 우선 노출한다
  durationSec: number;
  expectedBlinkRange: { low: number; high: number }; // 이 답변 길이(durationSec) 동안 일반적으로 예상되는 깜빡임 횟수 범위
  headMovement: number; // 0~100, 상대적인 움직임 정도(정확한 각도가 아님)
  gazeOffCenterRatio: number | null; // 0~100(%), 홍채가 눈 중앙에서 벗어나 있던 프레임 비율. 홍채 인식 실패 시 null
  frameCount: number;
}

// 성인 평상시 자연 깜빡임 빈도는 대략 분당 15~20회로 알려져 있다(의학 문헌 기준 대표값).
// 답변이 1분보다 훨씬 짧을 때(대부분 그렇다) "분당 환산"을 그대로 정상범위와 비교하면
// 작은 카운트 차이가 크게 부풀려져 보인다(예: 6초에 1회 차이 = 분당 10회 차이). 그래서
// 정상범위 자체를 답변 실제 길이(durationSec) 기준으로 환산해서 "그 시간 동안 몇 회가
// 보통이었는지"로 비교한다 - 60초로 부풀리는 대신 실제 답변 길이에 맞춘다.
const BLINK_BASELINE_LOW_PER_MIN = 15;
const BLINK_BASELINE_HIGH_PER_MIN = 20;

export function getExpectedBlinkRange(durationSec: number): { low: number; high: number } {
  if (durationSec <= 0) return { low: 0, high: 0 };
  return {
    low: Math.round((durationSec / 60) * BLINK_BASELINE_LOW_PER_MIN),
    high: Math.round((durationSec / 60) * BLINK_BASELINE_HIGH_PER_MIN),
  };
}

// 홍채가 눈 구석 사이 정중앙(0.5)에서 이만큼 이상 벗어나면 "정면을 안 보고 있다"로 센다.
// 0.15는 완벽한 응시 유지가 아니라 자연스러운 시선 흔들림 정도는 정상으로 봐주기 위한 여유폭이다.
const GAZE_CENTER_TOLERANCE = 0.15;

// MediaPipe의 eyeBlink blendshape는 조명·안경·카메라 각도에 따라 완전히 눈을 감아도
// 0.5까지 올라가지 않는 경우가 잦다. 기존 단일 0.5 임계값은 실제 눈 깜빡임을
// "0회"로 놓치는 원인이었다. 시작/종료 임계값을 나눠(히스테리시스) 민감도는 높이고,
// 임계값 근처에서 흔들릴 때 한 번의 깜빡임을 여러 번 세는 문제는 막는다.
const BLINK_START_THRESHOLD = 0.28;
const BLINK_END_THRESHOLD = 0.14;
// 실제 깜빡임 한 번은 보통 100~400ms 정도 걸리는데, 프레임마다(60fps 근처) 점수가
// 임계값 근처에서 미세하게 흔들리면 같은 깜빡임이 여러 번 카운트되는 문제가 있었다.
// 그래서 마지막으로 센 깜빡임 이후 이 시간(ms) 안에는 새로 세지 않는다(디바운스).
const MIN_BLINK_GAP_MS = 350;

export function summarizeFaceFrames(frames: FaceFrameSample[], durationSec: number): FaceMetrics | null {
  if (frames.length === 0) return null;

  let blinkCount = 0;
  let wasBlinking = false;
  let lastBlinkTimestamp = -Infinity;
  for (const frame of frames) {
    const isBlinking: boolean = wasBlinking
      ? frame.blinkScore > BLINK_END_THRESHOLD
      : frame.blinkScore > BLINK_START_THRESHOLD;
    if (isBlinking && !wasBlinking && frame.timestampMs - lastBlinkTimestamp > MIN_BLINK_GAP_MS) {
      blinkCount++;
      lastBlinkTimestamp = frame.timestampMs;
    }
    wasBlinking = isBlinking;
  }

  let totalMovement = 0;
  for (let i = 1; i < frames.length; i++) {
    const dx = frames[i].noseX - frames[i - 1].noseX;
    const dy = frames[i].noseY - frames[i - 1].noseY;
    totalMovement += Math.sqrt(dx * dx + dy * dy);
  }
  // 프레임 "개수"로 나누면 기기 주사율(60Hz/120Hz 등)이나 백그라운드 탭 스로틀링에 따라
  // 프레임 수 자체가 달라져서, 같은 물리적 움직임도 기기마다 값이 달라지는 문제가 있었다.
  // 프레임 타임스탬프로 실제 경과 시간(초)을 구해서 그걸로 나누면 기기와 무관해진다.
  const elapsedSec = frames.length > 1 ? (frames[frames.length - 1].timestampMs - frames[0].timestampMs) / 1000 : 0;
  const movementPerSec = elapsedSec > 0 ? totalMovement / elapsedSec : 0;

  let gazeSampleCount = 0;
  let gazeOffCenterCount = 0;
  for (const frame of frames) {
    if (frame.gazeRatio === null) continue;
    gazeSampleCount++;
    if (Math.abs(frame.gazeRatio - 0.5) > GAZE_CENTER_TOLERANCE) gazeOffCenterCount++;
  }

  const roundedDuration = Math.round(durationSec);
  return {
    blinkCount,
    blinkRatePerMin: durationSec > 0 ? Math.round((blinkCount / durationSec) * 60) : 0,
    durationSec: roundedDuration,
    expectedBlinkRange: getExpectedBlinkRange(durationSec),
    headMovement: Math.min(100, Math.round(movementPerSec * 90)),
    gazeOffCenterRatio: gazeSampleCount > 0 ? Math.round((gazeOffCenterCount / gazeSampleCount) * 100) : null,
    frameCount: frames.length,
  };
}
