import { describe, expect, it } from "vitest";
import {
  buildPracticeCategories,
  buildRealInterviewSlots,
  clampQuestionCount,
  normalizeQuestionCountInput,
  stepQuestionCount,
} from "./interviewConfig";

describe("interview question policy", () => {
  it("clamps practice to 2..5 and real to 5..10", () => {
    expect(clampQuestionCount("practice", 0)).toBe(2);
    expect(clampQuestionCount("practice", 9)).toBe(5);
    expect(clampQuestionCount("real", 2)).toBe(5);
    expect(clampQuestionCount("real", 12)).toBe(10);
    expect(clampQuestionCount("practice", Number.NaN)).toBe(2);
  });

  it("cycles comprehensive practice categories", () => {
    expect(buildPracticeCategories("종합", 4)).toEqual([
      "협업_리더십_커뮤니케이션",
      "문제해결_도전경험",
      "기술_직무역량",
      "가치관_자기관리",
    ]);
  });

  it.each([[5, 1], [7, 1], [8, 2], [10, 2]])(
    "allocates behavioral slots for %i questions",
    (count, behavioral) => {
      const slots = buildRealInterviewSlots(count);
      expect(slots).toHaveLength(count);
      expect(slots[0].kind).toBe("intro");
      expect(slots.at(-1)?.kind).toBe("closing");
      expect(slots.filter((slot) => slot.kind === "behavioral")).toHaveLength(behavioral);
    },
  );
});

// 2026-08-29: 질문 수 입력 컨트롤(QuestionCountControl)의 규칙은 전부 여기 순수 함수로
// 두고 UI 없이 검증한다 - 컴포넌트에는 "무엇을 호출할지"만 남긴다.
describe("question count input normalization", () => {
  it("falls back to the minimum when the input is empty or not a number", () => {
    // 입력창을 비운 채 포커스를 벗어나는 흐름 - 0이나 NaN이 그대로 남으면 안 된다.
    expect(normalizeQuestionCountInput("practice", "")).toBe(2);
    expect(normalizeQuestionCountInput("practice", "   ")).toBe(2);
    expect(normalizeQuestionCountInput("real", "abc")).toBe(5);
  });

  it("rounds decimals to the nearest whole question", () => {
    expect(normalizeQuestionCountInput("practice", "3.4")).toBe(3);
    expect(normalizeQuestionCountInput("practice", "3.6")).toBe(4);
    expect(normalizeQuestionCountInput("real", "9.5")).toBe(10);
  });

  it("clamps typed values that are outside the range", () => {
    expect(normalizeQuestionCountInput("practice", "99")).toBe(5);
    expect(normalizeQuestionCountInput("real", "-3")).toBe(5);
  });

  it("keeps the +/- buttons inside the range", () => {
    expect(stepQuestionCount("practice", 5, 1)).toBe(5);
    expect(stepQuestionCount("practice", 2, -1)).toBe(2);
    expect(stepQuestionCount("real", 9, 1)).toBe(10);
    expect(stepQuestionCount("real", 10, 1)).toBe(10);
    expect(stepQuestionCount("real", 5, -1)).toBe(5);
  });
});
