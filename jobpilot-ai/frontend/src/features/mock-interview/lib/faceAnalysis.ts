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
    const landmarker = await FaceLandmarker.createFromOptions(vision, {
      baseOptions: {
        modelAssetPath:
          "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task",
        delegate: "GPU",
      },
      outputFaceBlendshapes: true,
      outputFacialTransformationMatrixes: false,
      runningMode: "VIDEO",
      numFaces: 1,
    });
    cachedLandmarker = landmarker;
    return landmarker;
  })();

  return loadingPromise;
}

export interface FaceFrameSample {
  timestampMs: number;
  blinkScore: number; // eyeBlinkLeft/Right 평균 (0~1)
  noseX: number;
  noseY: number;
}

// MediaPipe face mesh 기준 코끝 근처 랜드마크 인덱스.
const NOSE_TIP_INDEX = 1;

// MediaPipe FaceMesh의 얼굴 윤곽선(턱선+이마) 인덱스를 순서대로 이어놓은 것.
// 478개 점을 다 찍으면 너무 촘촘해 보여서, 이 윤곽선만 선으로 그려 얼굴 위치를
// 보여주는 용도로 쓴다.
export const FACE_OVAL_INDICES = [
  10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150,
  136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10,
];

export function sampleFrame(result: FaceLandmarkerResult, timestampMs: number): FaceFrameSample | null {
  const landmarks = result.faceLandmarks?.[0];
  if (!landmarks || !landmarks[NOSE_TIP_INDEX]) return null;

  const blendshapes = result.faceBlendshapes?.[0]?.categories ?? [];
  const left = blendshapes.find((c) => c.categoryName === "eyeBlinkLeft")?.score ?? 0;
  const right = blendshapes.find((c) => c.categoryName === "eyeBlinkRight")?.score ?? 0;

  return {
    timestampMs,
    blinkScore: (left + right) / 2,
    noseX: landmarks[NOSE_TIP_INDEX].x,
    noseY: landmarks[NOSE_TIP_INDEX].y,
  };
}

export interface FaceMetrics {
  blinkCount: number;
  blinkRatePerMin: number;
  headMovement: number; // 0~100, 상대적인 움직임 정도(정확한 각도가 아님)
  frameCount: number;
}

const BLINK_THRESHOLD = 0.5;
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
    const isBlinking = frame.blinkScore > BLINK_THRESHOLD;
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
  const avgMovementPerFrame = totalMovement / Math.max(1, frames.length - 1);

  return {
    blinkCount,
    blinkRatePerMin: durationSec > 0 ? Math.round((blinkCount / durationSec) * 60) : 0,
    headMovement: Math.min(100, Math.round(avgMovementPerFrame * 5000)),
    frameCount: frames.length,
  };
}
