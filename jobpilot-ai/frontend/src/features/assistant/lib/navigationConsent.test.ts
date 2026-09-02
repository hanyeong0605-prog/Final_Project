import { describe, expect, it } from "vitest";
import { readNavigationIntent } from "./navigationConsent";

describe("readNavigationIntent", () => {
  it("사람이 실제로 치는 수락 표현을 폭넓게 받아준다", () => {
    const approvals = [
      "네", "넵", "넹", "네네", "예", "응", "응응", "웅", "어",
      "ㅇㅇ", "ㅇㅋ", "ㄱㄱ", "고고", "콜", "오케이", "오키",
      "그래", "그럼", "그러자", "그렇게 해줘", "좋아", "좋아요", "좋습니다",
      "이동", "이동해줘", "이동해 주세요", "이동할래", "가자", "가줘", "가 주세요",
      "갈래", "열어줘", "보여줘", "부탁해요", "진행해줘",
      "네 이동해줘", "응 그 페이지로 가줘", "그래 거기로 이동해 주세요",
      "네네 부탁해요", "좋아요 바로 이동해주세요", "ㅇㅇ 고고",
      "yes", "ok", "okay", "sure", "yes please", "go",
      "네!", "네~", "넵!!", "ㅇㅇ!!",
    ];
    for (const value of approvals) {
      expect(readNavigationIntent(value), value).toBe("approve");
    }
  });

  it("거절 표현은 이동하지 않고 거절로 읽는다", () => {
    const declines = [
      "아니", "아니요", "아뇨", "아니 괜찮아", "괜찮아요", "됐어요", "싫어",
      "안 갈래", "안갈래", "이동은 안 할래", "가지 마", "나중에", "다음에 할게",
      "필요 없어", "취소", "no", "nope",
    ];
    for (const value of declines) {
      expect(readNavigationIntent(value), value).toBe("decline");
    }
  });

  it("다른 내용이 섞인 새 질문은 수락으로 오인하지 않는다", () => {
    const questions = [
      // 이동 서술어가 있어도 다른 페이지를 가리키는 새 요청이다
      "채용공고 보여줘",
      "이력서 페이지로 이동해줘",
      "네 그런데 모의면접은 어떻게 해?",
      "이용권 얼마야",
      "면접 팁 알려줘",
      "그럼 자기소개서는 어떻게 써야 해?",
    ];
    for (const value of questions) {
      expect(readNavigationIntent(value), value).toBe("unrelated");
    }
  });

  it("빈 입력은 아무 의도도 아니다", () => {
    expect(readNavigationIntent("")).toBe("unrelated");
    expect(readNavigationIntent("   ")).toBe("unrelated");
  });
});
