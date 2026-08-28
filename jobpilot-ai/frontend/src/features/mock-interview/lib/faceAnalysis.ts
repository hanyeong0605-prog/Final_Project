import { FaceLandmarker, FilesetResolver, type FaceLandmarkerResult } from "@mediapipe/tasks-vision";

// 2026-08-04: 여기서도 "표정으로 감정을 판독"하지 않는다 - 눈 깜빡임 빈도, 고개 움직임처럼
// 근거를 바로 설명할 수 있는 객관적 신호만 뽑는다 (음성 쪽 audio_analysis.py와 같은 원칙).
// 얼굴 인식은 서버로 영상을 보내지 않고 브라우저 안에서 전부 처리한다(WASM) - 카메라
// 프레임을 굳이 서버에 스트리밍할 필요가 없고, 개인정보(얼굴 영상)를 안 보내는 게 낫다.
//
// 2026-08-29: 화상면접 코칭에 쓸 수 있도록 비언어 지표를 다시 짰다. 이전에는 코끝 2D 좌표의
// 이동량 하나로 "고개 움직임"을 만들었는데, 이건 카메라 위치와 사람의 평소 자세가 조금만
// 달라도 값이 통째로 달라졌다(노트북을 아래에 두고 쓰는 사람은 늘 "고개를 숙인" 값이 나온다).
// 이제 얼굴 변환 행렬에서 고개 회전(yaw/pitch)을 뽑고, 기기 점검 단계에서 잡은 기준 자세
// (FaceCalibration) 대비 상대값으로 계산한다. 코끝 이동량은 보조 지표로 남긴다.

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
      // 2026-08-29: 고개 회전(yaw/pitch)을 얻으려고 켰다. 랜드마크에서 직접 각도를 추정하는
      // 것보다 모델이 내주는 변환 행렬이 훨씬 안정적이다.
      outputFacialTransformationMatrixes: true,
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

// 프레임 하나에서 뽑아 보관하는 값. 원시 랜드마크 478개 배열은 여기 담지 않는다 - 세션
// 하나에 수천 프레임이 쌓이는데다, 얼굴 좌표 원본이 서버로 나갈 여지를 아예 없애려는 목적도
// 있다(설계 문서: 얼굴 영상과 원시 랜드마크는 브라우저 밖으로 내보내지 않는다).
export interface FaceFrameSample {
  timestampMs: number;
  /** 이 프레임에서 얼굴이 인식됐는지. false면 나머지 값은 의미가 없고, 유효 프레임 비율
   *  계산에만 쓰인다 - 인식 실패 프레임을 아예 버리면 "카메라에 얼굴이 거의 안 잡혔다"는
   *  사실 자체가 지표에서 사라진다. */
  valid: boolean;
  blinkScore: number; // eyeBlinkLeft/Right 평균 (0~1)
  noseX: number;
  noseY: number;
  gazeRatio: number | null; // 0~1, 0.5=양쪽 눈 구석 사이 정중앙, 홍채 인식 실패 시 null
  /** 고개 좌우 회전(도). 변환 행렬에서 추출하며 부호 규약 자체는 중요하지 않다 - 항상
   *  기준 자세(FaceCalibration) 대비 차이로만 쓴다. */
  yaw: number | null;
  /** 고개 상하 회전(도). yaw와 같은 이유로 상대값으로만 쓴다. */
  pitch: number | null;
  faceCenterX: number; // 얼굴 윤곽 바운딩 박스 중심 (0~1)
  faceCenterY: number;
  faceSize: number; // 윤곽 바운딩 박스의 긴 변 (0~1) - 화면에서 얼굴이 차지하는 크기
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
// 보여주는 용도로 쓴다. 2026-08-29부터 얼굴 중심·크기 계산에도 같은 점들을 쓴다.
export const FACE_OVAL_INDICES = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150,
  136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10,
];

// 눈 구석 두 점 사이에서 홍채가 상대적으로 어디 있는지(0~1, 0.5=가운데)를 계산한다.
// 3D 시선 방향을 추정하는 게 아니라 2D 상대 위치라, 고개를 심하게 돌리면 같이
// 왜곡될 수 있는 근사치다 - 그래서 아래 cameraGazeRatio는 이 값만 보지 않고 고개 회전과
// 함께 판단한다(설계 문서: 가로 홍채 위치만으로 시선을 단정하지 않는다).
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

const RAD_TO_DEG = 180 / Math.PI;

/** MediaPipe가 내려주는 4x4 얼굴 변환 행렬(열 우선)에서 고개 회전을 뽑는다.
 *
 *  절대 각도의 정확도나 부호 규약은 중요하지 않다 - 이 값은 언제나 기준 자세와의 차이로만
 *  쓰이기 때문이다(사람마다 카메라 높이도 평소 자세도 다르다). */
function extractHeadRotation(matrix: number[] | undefined): { yaw: number | null; pitch: number | null } {
  if (!matrix || matrix.length < 16) return { yaw: null, pitch: null };
  // 열 우선(column-major)이라 r[row][col] = matrix[col * 4 + row].
  const r02 = matrix[8];
  const r12 = matrix[9];
  const r22 = matrix[10];
  if ([r02, r12, r22].some((value) => typeof value !== "number" || Number.isNaN(value))) {
    return { yaw: null, pitch: null };
  }
  const yaw = Math.atan2(r02, r22) * RAD_TO_DEG;
  const pitch = Math.asin(Math.max(-1, Math.min(1, -r12))) * RAD_TO_DEG;
  return { yaw, pitch };
}

function faceOvalBounds(landmarks: { x: number; y: number }[]) {
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const index of FACE_OVAL_INDICES) {
    const point = landmarks[index];
    if (!point) continue;
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
  }
  if (!Number.isFinite(minX) || !Number.isFinite(minY)) return null;
  return {
    centerX: (minX + maxX) / 2,
    centerY: (minY + maxY) / 2,
    size: Math.max(maxX - minX, maxY - minY),
  };
}

/** 얼굴이 인식되지 않은 프레임. 유효 프레임 비율을 셀 수 있도록 null 대신 이 샘플을 남긴다. */
export function emptyFrameSample(timestampMs: number): FaceFrameSample {
  return {
    timestampMs,
    valid: false,
    blinkScore: 0,
    noseX: 0.5,
    noseY: 0.5,
    gazeRatio: null,
    yaw: null,
    pitch: null,
    faceCenterX: 0.5,
    faceCenterY: 0.5,
    faceSize: 0,
  };
}

export function sampleFrame(result: FaceLandmarkerResult, timestampMs: number): FaceFrameSample {
  const landmarks = result.faceLandmarks?.[0];
  if (!landmarks || !landmarks[NOSE_TIP_INDEX]) return emptyFrameSample(timestampMs);

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

  const { yaw, pitch } = extractHeadRotation(result.facialTransformationMatrixes?.[0]?.data as number[] | undefined);
  const bounds = faceOvalBounds(landmarks);

  return {
    timestampMs,
    valid: true,
    blinkScore: (left + right) / 2,
    noseX: landmarks[NOSE_TIP_INDEX].x,
    noseY: landmarks[NOSE_TIP_INDEX].y,
    gazeRatio,
    yaw,
    pitch,
    faceCenterX: bounds?.centerX ?? landmarks[NOSE_TIP_INDEX].x,
    faceCenterY: bounds?.centerY ?? landmarks[NOSE_TIP_INDEX].y,
    faceSize: bounds?.size ?? 0,
  };
}

/** 기기 점검 단계에서 잡아두는 "이 사람의 평소 정면 자세". */
export interface FaceCalibration {
  yaw: number;
  pitch: number;
  gazeRatio: number | null;
  faceCenterX: number;
  faceCenterY: number;
}

export interface FaceMetrics {
  blinkCount: number;
  blinkRatePerMin: number; // 참고용 환산치 - 화면에는 durationSec/expectedBlinkRange를 우선 노출한다
  durationSec: number;
  expectedBlinkRange: { low: number; high: number }; // 이 답변 길이(durationSec) 동안 일반적으로 예상되는 깜빡임 횟수 범위
  headMovement: number; // 0~100, 코끝 2D 이동량 기반 보조 지표(정확한 각도가 아님)
  gazeOffCenterRatio: number | null; // 0~100(%), 홍채가 기준 위치에서 벗어나 있던 프레임 비율. 홍채 인식 실패 시 null
  frameCount: number;
  /** 0~100(%), 기준 자세 대비 고개가 좌우/상하로 크게 돌아가 있던 유효 프레임 비율. 회전을
   *  한 번도 못 읽었으면 null. */
  headOffCenterRatio: number | null;
  cameraGazeRatio: number | null; // 0~100, 카메라 정면에 가까웠던(홍채+고개 모두) 프레임 비율
  faceCenteredRatio: number; // 0~100, 얼굴이 권장 화면 영역 안에 있던 비율
  validFrameRatio: number; // 0~100, 전체 프레임 중 얼굴이 인식된 비율
  confidence: FaceMetricsConfidence;
}

export type FaceMetricsConfidence = "sufficient" | "reference" | "insufficient";

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

// 아래 임계값들은 전부 이름을 붙여 export한다 - 테스트가 매직넘버를 다시 적지 않고 이 값을
// 그대로 쓰게 해서, 기준을 바꿀 때 테스트가 같이 따라오게 하려는 목적이다.

/** 홍채가 기준 위치에서 이만큼(0~1 스케일) 이상 벗어나면 "정면을 안 보고 있다"로 센다.
 *  완벽한 응시 유지가 아니라 자연스러운 시선 흔들림은 정상으로 봐주기 위한 여유폭이다. */
export const GAZE_CENTER_TOLERANCE = 0.15;
/** 기준 자세 대비 고개가 좌우로 이 각도(도)를 넘게 돌아가면 정면을 벗어난 것으로 센다. */
export const HEAD_YAW_TOLERANCE_DEG = 12;
/** 상하 회전은 좌우보다 조금 더 후하게 본다 - 자료를 잠깐 내려다보는 동작이 흔하다. */
export const HEAD_PITCH_TOLERANCE_DEG = 15;
/** 얼굴 중심이 기준 위치에서 이만큼(화면 비율) 이상 벗어나면 권장 영역 밖으로 본다. */
export const FACE_CENTER_TOLERANCE = 0.18;
/** 얼굴이 화면에서 차지하는 크기가 이보다 작으면 너무 멀리 있는 것으로 본다. */
export const FACE_MIN_SIZE = 0.12;
/** 이보다 짧은 답변은 수치를 근거로 쓰지 않는다. */
export const MIN_CONFIDENT_DURATION_SEC = 5;
/** 유효 프레임이 이보다 적으면 수치를 근거로 쓰지 않는다. */
export const MIN_CONFIDENT_VALID_FRAMES = 30;
/** 유효 프레임 비율이 이 아래면 "참고" 등급으로만 쓴다. */
export const REFERENCE_VALID_FRAME_RATIO = 0.6;
/** 기준 자세를 잡는 데 필요한 최소 연속 유효 구간(ms). */
export const CALIBRATION_MIN_SPAN_MS = 2000;
/** 기준 자세를 잡는 데 필요한 최소 연속 유효 프레임 수. */
export const CALIBRATION_MIN_FRAMES = 20;

// MediaPipe의 eyeBlink blendshape는 조명·안경·카메라 각도에 따라 완전히 눈을 감아도
// 0.5까지 올라가지 않는 경우가 잦다. 기존 단일 0.5 임계값은 실제 눈 깜빡임을
// "0회"로 놓치는 원인이었다. 시작/종료 임계값을 나눠(히스테리시스) 민감도는 높이고,
// 임계값 근처에서 흔들릴 때 한 번의 깜빡임을 여러 번 세는 문제는 막는다.
const BLINK_START_THRESHOLD = 0.28;
const BLINK_END_THRESHOLD = 0.14;
// 실제 깜빡임 한 번은 보통 100~400ms 정도 걸리는데, 프레임마다(60fps 근처) 점수가
// 임계값 근처에서 미세하게 흔들리면 같은 깜빡임이 여러 번 카운트되는 문제가 있었다.
// 그래서 마지막으로 센 깜빡임 이후 이 시간(ms) 안에는 새로 세지 않는다(디바운스).
const BLINK_MIN_GAP_MS = 350;

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
}

/** 기기 점검 단계 샘플에서 기준 자세를 만든다.
 *
 *  "연속으로 얼굴이 잘 잡힌 구간"에서만 중앙값을 낸다 - 카메라를 켜는 순간이나 자리를 잡는
 *  동안의 흔들리는 프레임까지 섞으면 기준 자세 자체가 삐뚤어진다. 조건을 못 채우면 null이고,
 *  호출부는 보정 없이 면접을 진행하되 결과 신뢰도를 insufficient로 다뤄야 한다. */
export function buildCalibration(samples: FaceFrameSample[]): FaceCalibration | null {
  let best: FaceFrameSample[] = [];
  let current: FaceFrameSample[] = [];
  for (const sample of samples) {
    if (sample.valid) {
      current.push(sample);
      if (current.length > best.length) best = current;
    } else {
      current = [];
    }
  }
  if (best.length < CALIBRATION_MIN_FRAMES) return null;
  const span = best[best.length - 1].timestampMs - best[0].timestampMs;
  if (span < CALIBRATION_MIN_SPAN_MS) return null;

  const yaws = best.map((s) => s.yaw).filter((v): v is number => v !== null);
  const pitches = best.map((s) => s.pitch).filter((v): v is number => v !== null);
  const gazes = best.map((s) => s.gazeRatio).filter((v): v is number => v !== null);
  return {
    yaw: yaws.length > 0 ? median(yaws) : 0,
    pitch: pitches.length > 0 ? median(pitches) : 0,
    gazeRatio: gazes.length > 0 ? median(gazes) : null,
    faceCenterX: median(best.map((s) => s.faceCenterX)),
    faceCenterY: median(best.map((s) => s.faceCenterY)),
  };
}

export function summarizeFaceFrames(
  frames: FaceFrameSample[],
  durationSec: number,
  calibration?: FaceCalibration | null,
): FaceMetrics | null {
  if (frames.length === 0) return null;
  const validFrames = frames.filter((frame) => frame.valid);

  let blinkCount = 0;
  let wasBlinking = false;
  let lastBlinkTimestamp = -Infinity;
  for (const frame of validFrames) {
    const isBlinking: boolean = wasBlinking
      ? frame.blinkScore > BLINK_END_THRESHOLD
      : frame.blinkScore > BLINK_START_THRESHOLD;
    if (isBlinking && !wasBlinking && frame.timestampMs - lastBlinkTimestamp > BLINK_MIN_GAP_MS) {
      blinkCount++;
      lastBlinkTimestamp = frame.timestampMs;
    }
    wasBlinking = isBlinking;
  }

  let totalMovement = 0;
  for (let i = 1; i < validFrames.length; i++) {
    const dx = validFrames[i].noseX - validFrames[i - 1].noseX;
    const dy = validFrames[i].noseY - validFrames[i - 1].noseY;
    totalMovement += Math.sqrt(dx * dx + dy * dy);
  }
  // 프레임 "개수"로 나누면 기기 주사율(60Hz/120Hz 등)이나 백그라운드 탭 스로틀링에 따라
  // 프레임 수 자체가 달라져서, 같은 물리적 움직임도 기기마다 값이 달라지는 문제가 있었다.
  // 프레임 타임스탬프로 실제 경과 시간(초)을 구해서 그걸로 나누면 기기와 무관해진다.
  const elapsedSec =
    validFrames.length > 1
      ? (validFrames[validFrames.length - 1].timestampMs - validFrames[0].timestampMs) / 1000
      : 0;
  const movementPerSec = elapsedSec > 0 ? totalMovement / elapsedSec : 0;

  // 기준 자세가 없으면 "화면 정중앙을 보고 있었다"고 가정한다 - 보정을 못 한 세션이라
  // 아래에서 신뢰도를 insufficient로 낮추므로, 이 가정이 수치를 근거로 쓰이지는 않는다.
  const baseYaw = calibration?.yaw ?? 0;
  const basePitch = calibration?.pitch ?? 0;
  const baseGaze = calibration?.gazeRatio ?? 0.5;
  const baseCenterX = calibration?.faceCenterX ?? 0.5;
  const baseCenterY = calibration?.faceCenterY ?? 0.5;

  let gazeSampleCount = 0;
  let gazeOffCenterCount = 0;
  let headSampleCount = 0;
  let headOffCenterCount = 0;
  let cameraGazeCount = 0;
  let faceCenteredCount = 0;

  for (const frame of validFrames) {
    const headOffCenter =
      frame.yaw !== null && frame.pitch !== null
        ? Math.abs(frame.yaw - baseYaw) > HEAD_YAW_TOLERANCE_DEG ||
          Math.abs(frame.pitch - basePitch) > HEAD_PITCH_TOLERANCE_DEG
        : null;
    if (headOffCenter !== null) {
      headSampleCount++;
      if (headOffCenter) headOffCenterCount++;
    }

    const gazeOffCenter =
      frame.gazeRatio !== null ? Math.abs(frame.gazeRatio - baseGaze) > GAZE_CENTER_TOLERANCE : null;
    if (gazeOffCenter !== null) {
      gazeSampleCount++;
      if (gazeOffCenter) gazeOffCenterCount++;
    }

    // 카메라 응시는 홍채만으로 단정하지 않는다 - 고개가 크게 돌아가 있으면 홍채가 눈
    // 가운데 있어도 카메라를 보는 게 아니다(설계 문서의 근사 방식).
    if (gazeOffCenter === false && headOffCenter !== true) cameraGazeCount++;

    const centered =
      Math.abs(frame.faceCenterX - baseCenterX) <= FACE_CENTER_TOLERANCE &&
      Math.abs(frame.faceCenterY - baseCenterY) <= FACE_CENTER_TOLERANCE &&
      frame.faceSize >= FACE_MIN_SIZE;
    if (centered) faceCenteredCount++;
  }

  const validFrameRatio = validFrames.length / frames.length;
  const confidence: FaceMetricsConfidence =
    durationSec < MIN_CONFIDENT_DURATION_SEC ||
    validFrames.length < MIN_CONFIDENT_VALID_FRAMES ||
    !calibration
      ? "insufficient"
      : validFrameRatio < REFERENCE_VALID_FRAME_RATIO
        ? "reference"
        : "sufficient";

  const percent = (numerator: number, denominator: number) =>
    denominator > 0 ? Math.round((numerator / denominator) * 100) : 0;

  return {
    blinkCount,
    blinkRatePerMin: durationSec > 0 ? Math.round((blinkCount / durationSec) * 60) : 0,
    durationSec: Math.round(durationSec),
    expectedBlinkRange: getExpectedBlinkRange(durationSec),
    headMovement: Math.min(100, Math.round(movementPerSec * 90)),
    gazeOffCenterRatio: gazeSampleCount > 0 ? percent(gazeOffCenterCount, gazeSampleCount) : null,
    frameCount: frames.length,
    headOffCenterRatio: headSampleCount > 0 ? percent(headOffCenterCount, headSampleCount) : null,
    cameraGazeRatio: validFrames.length > 0 ? percent(cameraGazeCount, validFrames.length) : null,
    faceCenteredRatio: validFrames.length > 0 ? percent(faceCenteredCount, validFrames.length) : 0,
    validFrameRatio: percent(validFrames.length, frames.length),
    confidence,
  };
}
