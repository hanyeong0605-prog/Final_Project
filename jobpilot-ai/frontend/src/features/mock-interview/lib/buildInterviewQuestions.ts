import {
  buildPracticeCategories,
  buildRealInterviewSlots,
  clampQuestionCount,
  type InterviewKind,
  type InterviewQuestionSource,
  type InterviewType,
} from "../model/interviewConfig";

// 2026-08-29: 세션 질문 조립을 페이지에서 떼어냈다. 원래 MockInterviewPage의
// buildSessionQuestions 안에 무료/실전 두 흐름이 통째로 들어 있었는데, 컴포넌트 상태와
// 얽혀 있어서 "5개짜리 세션에서 행동 질문이 몇 개 나오는가" 같은 걸 확인하려면 화면을
// 직접 돌려보는 수밖에 없었다. fetchQuestion을 주입받는 순수 함수로 빼서 테스트한다.
//
// 두 모드의 호출 방식이 다른 게 핵심이다:
// - 무료는 순차 호출이라 매번 "지금까지 확정된 질문"을 exclude로 넘길 수 있어서 코퍼스
//   안에서 중복이 원천 차단된다.
// - 실전은 슬롯별로 병렬 호출한다(질문 5~10개를 하나씩 기다리면 시작이 너무 느리다).
//   대신 서로의 결과를 모르므로 중복이 날 수 있고, 그때만 코퍼스로 대체한다.

export const SELF_INTRO_QUESTION = "간단하게 자기소개 부탁드립니다.";

/** 실전면접의 마지막 고정 질문. 회사를 골랐으면 회사명을 넣는다. */
export function buildClosingQuestion(companyName?: string): string {
  const company = companyName?.trim();
  return company
    ? `${company}에 입사하게 된다면 어떤 목표를 이루고 싶은지 말씀해 주세요.`
    : "입사 후 이루고 싶은 목표와 포부를 말씀해 주세요.";
}

export type FetchInterviewQuestion = (options: {
  mode: InterviewKind;
  source?: InterviewQuestionSource;
  job?: string;
  category?: string;
  angleHint?: string;
  exclude?: string[];
  jobPostingId?: number;
  memberId?: number;
  techSummary?: string;
}) => Promise<{ question: string }>;

export type InterviewQuestionConfig = {
  kind: InterviewKind;
  questionCount: number;
  /** 무료 전용 - 이 유형의 카테고리만 순환한다. */
  interviewType: InterviewType;
  /** 실전 전용 - 질문 근거. */
  source: InterviewQuestionSource;
  job?: string;
  techSummary?: string;
  memberId?: number;
  jobPostingId?: number;
  companyName?: string;
};

export type InterviewQuestionDependencies = {
  fetchQuestion: FetchInterviewQuestion;
};

/** 생성도 코퍼스 대체도 실패해서 세션을 채우지 못한 경우. 호출부는 중복 질문으로 자리를
 *  메우지 말고 사용자에게 다시 시도할 기회를 줘야 한다(설계 문서 "오류 및 경계 처리"). */
export class InterviewQuestionBuildError extends Error {
  constructor(message = "질문을 충분히 준비하지 못했어요. 잠시 후 다시 시도해 주세요.") {
    super(message);
    this.name = "InterviewQuestionBuildError";
  }
}

// 앞뒤 공백/개행만 다른 응답(LLM에서 흔하다)도 같은 질문으로 본다.
const normalize = (text: string) => text.replace(/\s+/g, " ").trim();

function isDuplicate(questions: string[], candidate: string): boolean {
  const normalized = normalize(candidate);
  if (!normalized) return true;
  return questions.some((question) => normalize(question) === normalized);
}

export async function buildInterviewQuestions(
  config: InterviewQuestionConfig,
  { fetchQuestion }: InterviewQuestionDependencies,
): Promise<string[]> {
  const count = clampQuestionCount(config.kind, config.questionCount);
  return config.kind === "practice"
    ? buildPracticeQuestions(config, count, fetchQuestion)
    : buildRealQuestions(config, count, fetchQuestion);
}

async function buildPracticeQuestions(
  config: InterviewQuestionConfig,
  count: number,
  fetchQuestion: FetchInterviewQuestion,
): Promise<string[]> {
  const questions: string[] = [SELF_INTRO_QUESTION];

  for (const category of buildPracticeCategories(config.interviewType, count - 1)) {
    try {
      // 무료는 목표 직무만 쓴다 - 프로젝트/기술 요약/공고는 넘기지 않는다(설계 문서
      // "무료 모의면접" 절). 서버도 mode=practice면 그 값들을 아예 보지 않는다.
      const result = await fetchQuestion({
        mode: "practice",
        job: config.job,
        category,
        exclude: [...questions],
      });
      if (!isDuplicate(questions, result.question)) questions.push(result.question);
    } catch {
      // 코퍼스 호출 하나가 실패해도 남은 질문은 계속 시도한다 - 아래에서 총 개수를 본다.
    }
  }

  if (questions.length < count) throw new InterviewQuestionBuildError();
  return questions;
}

async function buildRealQuestions(
  config: InterviewQuestionConfig,
  count: number,
  fetchQuestion: FetchInterviewQuestion,
): Promise<string[]> {
  const slots = buildRealInterviewSlots(count);
  const closing = buildClosingQuestion(config.companyName);
  const middleSlots = slots.filter((slot) => slot.kind !== "intro" && slot.kind !== "closing");

  // 병렬로 쏘고, 결과는 슬롯 순서대로 조립한다 - 응답이 먼저 온 순서가 아니라 면접 순서가
  // 유지돼야 한다.
  const generated = await Promise.allSettled(
    middleSlots.map((slot) =>
      fetchQuestion({
        mode: "real",
        source: config.source,
        job: config.job,
        techSummary: config.techSummary,
        category: slot.category,
        angleHint: slot.angle,
        exclude: [SELF_INTRO_QUESTION, closing],
        memberId: config.memberId,
        jobPostingId: config.jobPostingId,
      }),
    ),
  );

  const questions: string[] = [SELF_INTRO_QUESTION];
  const unfilled: number[] = [];
  middleSlots.forEach((_slot, index) => {
    const result = generated[index];
    const candidate = result.status === "fulfilled" ? result.value.question : null;
    if (candidate && !isDuplicate([...questions, closing], candidate)) {
      questions.push(candidate);
      return;
    }
    // 실패했거나 이미 나온 질문과 겹친 슬롯만 기억해뒀다가 코퍼스로 메운다.
    unfilled.push(index);
  });

  for (const index of unfilled) {
    try {
      const result = await fetchQuestion({
        mode: "practice",
        job: config.job,
        category: middleSlots[index].category,
        exclude: [...questions, closing],
      });
      if (!isDuplicate([...questions, closing], result.question)) questions.push(result.question);
    } catch {
      // 마지막 개수 검사에서 걸린다.
    }
  }

  questions.push(closing);
  if (questions.length < count) throw new InterviewQuestionBuildError();
  return questions;
}
