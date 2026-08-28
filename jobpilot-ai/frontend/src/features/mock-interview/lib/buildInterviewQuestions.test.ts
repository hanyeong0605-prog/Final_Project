import { describe, expect, it, vi } from "vitest";

import {
  InterviewQuestionBuildError,
  SELF_INTRO_QUESTION,
  buildInterviewQuestions,
  type FetchInterviewQuestion,
} from "./buildInterviewQuestions";

// 실제 네트워크 대신 fetchQuestion을 주입해서, "어떤 요청을 어떤 순서로 보냈는가"와
// "조립된 질문 목록"만 검증한다. 무료는 순차, 실전은 병렬이라 호출 순서 자체가 설계의
// 일부다(누적 exclude를 쓸 수 있는지가 여기서 갈린다).
function makeFetch(
  handler: (options: Parameters<FetchInterviewQuestion>[0], index: number) => string | Promise<string>,
) {
  const calls: Parameters<FetchInterviewQuestion>[0][] = [];
  const fetchQuestion: FetchInterviewQuestion = async (options) => {
    const index = calls.length;
    calls.push(options);
    return { question: await handler(options, index) };
  };
  return { fetchQuestion, calls };
}

const practiceConfig = {
  kind: "practice" as const,
  questionCount: 4,
  interviewType: "종합" as const,
  source: "spec" as const,
};

const realConfig = {
  kind: "real" as const,
  questionCount: 5,
  interviewType: "종합" as const,
  source: "spec_company" as const,
  memberId: 7,
  jobPostingId: 33,
  companyName: "잡어드림",
};

describe("buildInterviewQuestions - 무료 모의면접", () => {
  it("starts with the self introduction and fills the rest from the corpus", async () => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `코퍼스 질문 ${index}`);

    const questions = await buildInterviewQuestions(practiceConfig, { fetchQuestion });

    expect(questions).toHaveLength(4);
    expect(questions[0]).toBe(SELF_INTRO_QUESTION);
    expect(questions.slice(1)).toEqual(["코퍼스 질문 0", "코퍼스 질문 1", "코퍼스 질문 2"]);
    expect(calls.every((call) => call.mode === "practice")).toBe(true);
  });

  it("never asks the paid path for a free session", async () => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `코퍼스 질문 ${index}`);

    await buildInterviewQuestions(practiceConfig, { fetchQuestion });

    expect(calls.some((call) => call.mode === "real")).toBe(false);
    expect(calls.some((call) => call.memberId !== undefined)).toBe(false);
    expect(calls.some((call) => call.jobPostingId !== undefined)).toBe(false);
  });

  it("passes everything decided so far as exclude (sequential calls can do this)", async () => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `코퍼스 질문 ${index}`);

    await buildInterviewQuestions(practiceConfig, { fetchQuestion });

    expect(calls[0].exclude).toEqual([SELF_INTRO_QUESTION]);
    expect(calls[1].exclude).toEqual([SELF_INTRO_QUESTION, "코퍼스 질문 0"]);
    expect(calls[2].exclude).toEqual([SELF_INTRO_QUESTION, "코퍼스 질문 0", "코퍼스 질문 1"]);
  });

  it("cycles the categories of the chosen interview type", async () => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `코퍼스 질문 ${index}`);

    await buildInterviewQuestions({ ...practiceConfig, interviewType: "인성", questionCount: 4 }, { fetchQuestion });

    expect(calls.map((call) => call.category)).toEqual([
      "가치관_자기관리",
      "협업_리더십_커뮤니케이션",
      "가치관_자기관리",
    ]);
  });
});

describe("buildInterviewQuestions - 실전면접", () => {
  it("fixes the first and last questions and keeps slot order", async () => {
    const { fetchQuestion } = makeFetch((options) => `생성 질문 (${options.angleHint ?? "각도없음"})`);

    const questions = await buildInterviewQuestions(realConfig, { fetchQuestion });

    expect(questions).toHaveLength(5);
    expect(questions[0]).toBe(SELF_INTRO_QUESTION);
    expect(questions.at(-1)).toContain("잡어드림");
  });

  it("uses a generic closing question when no company was chosen", async () => {
    const { fetchQuestion } = makeFetch((_options, index) => `생성 질문 ${index}`);

    const questions = await buildInterviewQuestions(
      { ...realConfig, source: "spec", jobPostingId: undefined, companyName: undefined },
      { fetchQuestion },
    );

    expect(questions.at(-1)).toBe("입사 후 이루고 싶은 목표와 포부를 말씀해 주세요.");
    expect(questions.at(-1)).not.toContain("잡어드림");
  });

  it("sends the real mode with the chosen source and ids", async () => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `생성 질문 ${index}`);

    await buildInterviewQuestions(realConfig, { fetchQuestion });

    expect(calls.every((call) => call.mode === "real")).toBe(true);
    expect(calls.every((call) => call.source === "spec_company")).toBe(true);
    expect(calls.every((call) => call.memberId === 7)).toBe(true);
    expect(calls.every((call) => call.jobPostingId === 33)).toBe(true);
  });

  it.each([
    [5, 1],
    [7, 1],
    [8, 2],
    [10, 2],
  ])("requests %i questions with the right number of behavioral angles", async (count, behavioral) => {
    const { fetchQuestion, calls } = makeFetch((_options, index) => `생성 질문 ${index}`);

    const questions = await buildInterviewQuestions({ ...realConfig, questionCount: count }, { fetchQuestion });

    expect(questions).toHaveLength(count);
    // 자기소개/포부를 뺀 중간 슬롯만 생성 요청을 보낸다.
    expect(calls).toHaveLength(count - 2);
    expect(calls.filter((call) => call.category !== "기술_직무역량")).toHaveLength(behavioral);
  });

  it("replaces a duplicate generated question with a corpus question", async () => {
    // 실전은 슬롯별 병렬 호출이라 서로의 결과를 모른다 - 같은 질문이 두 번 나올 수 있고,
    // 그때만 코퍼스(무료 경로)로 대체한다.
    const { fetchQuestion, calls } = makeFetch((options, index) =>
      options.mode === "real" ? "똑같은 질문입니다." : `코퍼스 대체 ${index}`,
    );

    const questions = await buildInterviewQuestions(realConfig, { fetchQuestion });

    expect(new Set(questions).size).toBe(questions.length);
    expect(calls.some((call) => call.mode === "practice")).toBe(true);
  });

  it("falls back to the corpus when generation fails", async () => {
    const { fetchQuestion, calls } = makeFetch((options, index) => {
      if (options.mode === "real") throw new Error("Gemini 실패");
      return `코퍼스 대체 ${index}`;
    });

    const questions = await buildInterviewQuestions(realConfig, { fetchQuestion });

    expect(questions).toHaveLength(5);
    expect(questions.slice(1, -1).every((question) => question.startsWith("코퍼스 대체"))).toBe(true);
    expect(calls.filter((call) => call.mode === "practice")).toHaveLength(3);
  });

  it("throws instead of padding with duplicates when it cannot fill the session", async () => {
    // 생성도 코퍼스도 전부 실패 - 중복 질문으로 자리를 채우느니 사용자에게 다시 시도할
    // 기회를 주는 게 낫다(설계 문서 "오류 및 경계 처리").
    const { fetchQuestion } = makeFetch(() => {
      throw new Error("전부 실패");
    });

    await expect(buildInterviewQuestions(realConfig, { fetchQuestion })).rejects.toBeInstanceOf(
      InterviewQuestionBuildError,
    );
  });

  it("treats whitespace-only differences as duplicates", async () => {
    const seen = vi.fn();
    const { fetchQuestion } = makeFetch((options, index) => {
      seen(options.mode);
      if (options.mode === "real") return index === 0 ? "같은  질문 입니다." : "같은 질문   입니다.";
      return `코퍼스 대체 ${index}`;
    });

    const questions = await buildInterviewQuestions(realConfig, { fetchQuestion });

    const normalized = questions.map((question) => question.replace(/\s+/g, " ").trim());
    expect(new Set(normalized).size).toBe(normalized.length);
  });
});
