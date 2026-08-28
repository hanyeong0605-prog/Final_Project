import { describe, expect, it } from "vitest";

import {
  CALIBRATION_MIN_FRAMES,
  FACE_MIN_SIZE,
  HEAD_YAW_TOLERANCE_DEG,
  MIN_CONFIDENT_VALID_FRAMES,
  REFERENCE_VALID_FRAME_RATIO,
  buildCalibration,
  summarizeFaceFrames,
  type FaceCalibration,
  type FaceFrameSample,
} from "./faceAnalysis";

// 기준 자세를 왼쪽으로 살짝 튼 사람(노트북을 옆에 두고 쓰는 흔한 자세)을 가정한다 -
// 보정이 없으면 이 사람은 가만히 있어도 계속 "고개를 돌리고 있다"로 집계된다.
const calibration: FaceCalibration = {
  yaw: -8,
  pitch: 4,
  gazeRatio: 0.5,
  faceCenterX: 0.5,
  faceCenterY: 0.5,
};

function makeFrame(overrides: Partial<FaceFrameSample> = {}, index = 0): FaceFrameSample {
  return {
    timestampMs: index * 100,
    valid: true,
    blinkScore: 0,
    noseX: 0.5,
    noseY: 0.5,
    gazeRatio: calibration.gazeRatio,
    yaw: calibration.yaw,
    pitch: calibration.pitch,
    faceCenterX: calibration.faceCenterX,
    faceCenterY: calibration.faceCenterY,
    faceSize: 0.4,
    ...overrides,
  };
}

function makeFrames(count: number, overrides: Partial<FaceFrameSample> = {}): FaceFrameSample[] {
  return Array.from({ length: count }, (_, index) => makeFrame(overrides, index));
}

describe("buildCalibration", () => {
  it("takes the median of the longest continuous valid run", () => {
    const frames = [
      ...makeFrames(5, { yaw: 40 }), // 자리를 잡는 동안의 흔들리는 구간
      makeFrame({ valid: false }),
      ...makeFrames(CALIBRATION_MIN_FRAMES + 10, { yaw: -8, pitch: 4 }),
    ].map((frame, index) => ({ ...frame, timestampMs: index * 100 }));

    const result = buildCalibration(frames);

    expect(result?.yaw).toBe(-8);
    expect(result?.pitch).toBe(4);
  });

  it("returns null when the valid run is too short", () => {
    expect(buildCalibration(makeFrames(CALIBRATION_MIN_FRAMES - 1))).toBeNull();
  });

  it("returns null when the run has enough frames but spans too little time", () => {
    // 프레임은 충분한데 1초도 안 되는 구간(고프레임 카메라) - 기준 자세로 쓰기엔 짧다.
    const dense = makeFrames(CALIBRATION_MIN_FRAMES + 5).map((frame, index) => ({
      ...frame,
      timestampMs: index * 10,
    }));

    expect(buildCalibration(dense)).toBeNull();
  });
});

describe("summarizeFaceFrames - 고개 회전은 기준 자세 대비로 잰다", () => {
  it("does not flag the calibrated resting pose as off-center", () => {
    const metrics = summarizeFaceFrames(makeFrames(120), 30, calibration);

    expect(metrics?.headOffCenterRatio).toBe(0);
  });

  it("measures head rotation relative to calibration", () => {
    const rotated = makeFrames(120, { yaw: calibration.yaw + HEAD_YAW_TOLERANCE_DEG + 4 });

    const metrics = summarizeFaceFrames(rotated, 30, calibration);

    expect(metrics?.headOffCenterRatio).toBe(100);
  });

  it("would misread the same pose without calibration", () => {
    // 보정 없이 절대 각도로 재면, 가만히 있는 사람도 기준(0도)에서 8도 틀어져 있으므로
    // 허용 범위를 넘는 순간 통째로 "고개를 돌렸다"가 된다 - 보정이 필요한 이유.
    const tilted = makeFrames(120, { yaw: -(HEAD_YAW_TOLERANCE_DEG + 5) });

    expect(summarizeFaceFrames(tilted, 30, null)?.headOffCenterRatio).toBe(100);
    expect(summarizeFaceFrames(tilted, 30, { ...calibration, yaw: -(HEAD_YAW_TOLERANCE_DEG + 5) })?.headOffCenterRatio).toBe(0);
  });

  it("returns null head ratio when the model never gave a rotation", () => {
    const metrics = summarizeFaceFrames(makeFrames(120, { yaw: null, pitch: null }), 30, calibration);

    expect(metrics?.headOffCenterRatio).toBeNull();
  });
});

describe("summarizeFaceFrames - 카메라 응시와 화면 중앙 유지", () => {
  it("does not count a centered iris as camera gaze when the head is turned away", () => {
    // 홍채는 눈 가운데 있지만 고개가 돌아간 상태 - 가로 홍채 위치만 보면 "정면 응시"로
    // 잘못 집계된다(설계 문서: 홍채 위치만으로 시선을 단정하지 않는다).
    const turned = makeFrames(120, { yaw: calibration.yaw + HEAD_YAW_TOLERANCE_DEG + 10 });

    const metrics = summarizeFaceFrames(turned, 30, calibration);

    expect(metrics?.cameraGazeRatio).toBe(0);
    expect(metrics?.gazeOffCenterRatio).toBe(0); // 홍채만 보면 "정면"이었다
  });

  it("counts a calibrated, forward-facing frame as camera gaze", () => {
    const metrics = summarizeFaceFrames(makeFrames(120), 30, calibration);

    expect(metrics?.cameraGazeRatio).toBe(100);
    expect(metrics?.faceCenteredRatio).toBe(100);
  });

  it("treats a face that is too small as outside the recommended area", () => {
    const faraway = makeFrames(120, { faceSize: FACE_MIN_SIZE - 0.01 });

    expect(summarizeFaceFrames(faraway, 30, calibration)?.faceCenteredRatio).toBe(0);
  });

  it("treats a drifting face center as outside the recommended area", () => {
    const drifted = makeFrames(120, { faceCenterX: calibration.faceCenterX + 0.4 });

    expect(summarizeFaceFrames(drifted, 30, calibration)?.faceCenteredRatio).toBe(0);
  });
});

describe("summarizeFaceFrames - 분석 신뢰도", () => {
  it("marks sparse short recordings as insufficient", () => {
    expect(summarizeFaceFrames(makeFrames(8), 2, calibration)?.confidence).toBe("insufficient");
  });

  it("marks a long answer with too few valid frames as insufficient", () => {
    expect(summarizeFaceFrames(makeFrames(MIN_CONFIDENT_VALID_FRAMES - 1), 30, calibration)?.confidence).toBe(
      "insufficient",
    );
  });

  it("marks an uncalibrated session as insufficient even when the frames look fine", () => {
    // 보정을 못 했으면 수치 자체는 나오지만 근거로 쓰면 안 된다.
    expect(summarizeFaceFrames(makeFrames(300), 30, null)?.confidence).toBe("insufficient");
  });

  it("downgrades to reference when the face was often not detected", () => {
    const detected = 100;
    const missing = Math.ceil(detected / REFERENCE_VALID_FRAME_RATIO) - detected + 10;
    const frames = [...makeFrames(detected), ...makeFrames(missing, { valid: false })].map((frame, index) => ({
      ...frame,
      timestampMs: index * 100,
    }));

    const metrics = summarizeFaceFrames(frames, 30, calibration);

    expect(metrics?.confidence).toBe("reference");
    expect(metrics?.validFrameRatio).toBeLessThan(REFERENCE_VALID_FRAME_RATIO * 100);
  });

  it("reports sufficient for a well-recorded answer", () => {
    const metrics = summarizeFaceFrames(makeFrames(300), 30, calibration);

    expect(metrics?.confidence).toBe("sufficient");
    expect(metrics?.validFrameRatio).toBe(100);
  });

  it("ignores invalid frames when counting blinks", () => {
    // 얼굴을 못 잡은 프레임의 blinkScore(0)가 깜빡임 판정에 섞이면 안 된다.
    const frames = [
      ...makeFrames(10, { blinkScore: 0 }),
      ...makeFrames(3, { valid: false, blinkScore: 0.9 }),
      ...makeFrames(10, { blinkScore: 0 }),
    ].map((frame, index) => ({ ...frame, timestampMs: index * 100 }));

    expect(summarizeFaceFrames(frames, 10, calibration)?.blinkCount).toBe(0);
  });
});
