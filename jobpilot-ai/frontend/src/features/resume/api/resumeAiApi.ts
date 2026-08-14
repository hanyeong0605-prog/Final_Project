import type {
  CompanyQuestionsResult,
  ProjectCritiqueResult,
  ProjectDraftResult,
  SelfIntroductionCritiqueResult,
  SelfIntroductionDraftResult,
} from "../model/resume.types";

// mockInterviewApi.ts와 같은 이유로 Spring 백엔드가 아니라 파이썬 ai-server로 직접
// 보낸다(/ai-api 프록시 - vite.config.ts). 저장(백엔드 CRUD)과 생성(ai-server)이 분리된
// 구조라 이 파일과 resumeApi.ts는 서로 호출하지 않는다 - 프론트가 둘을 순서대로 조합한다.

async function postAi<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`/ai-api/resume${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { detail?: string } | null;
    throw new Error(error?.detail ?? `요청 실패 (HTTP ${response.status})`);
  }
  return response.json();
}

async function getAi<T>(path: string): Promise<T> {
  const response = await fetch(`/ai-api/resume${path}`);
  if (!response.ok) throw new Error(`요청 실패 (HTTP ${response.status})`);
  return response.json();
}

export const fetchSelfIntroductionQuestions = () => getAi<{ questions: string[] }>("/self-introduction/questions");

// 2026-08-13: questions를 넘기면 회사 양식 파싱 결과(아래 parseCompanyQuestions) 기준으로,
// 안 넘기면(undefined) 기존처럼 서버 기본 GUIDED_QUESTIONS 기준으로 초안을 만든다.
// 2026-08-14: resumeContext 추가 - 업로드한 이력서에서 추출한 텍스트(ResumeDocumentSection과
// 같은 백엔드 /resume-documents/extract 결과)를 넘기면, 답변을 다듬을 때 배경 참고자료로
// 활용한다(ai-server self_introduction._career_context 참고).
export const generateSelfIntroductionDraft = (
  job: string,
  techSummary: string,
  answers: string[],
  questions?: string[],
  resumeContext?: string,
) =>
  postAi<SelfIntroductionDraftResult>("/self-introduction/generate", {
    job,
    tech_summary: techSummary,
    answers,
    questions: questions ?? [],
    resume_context: resumeContext ?? "",
  });

// 회사 채용 페이지에서 그대로 복사한 자소서 문항 안내 텍스트를 넘기면, 실제 질문 목록만
// 추출해서 돌려준다 - "질문식으로 작성" 채팅에서 기본 4문항 대신 이 목록을 순서대로 묻는다.
export const parseCompanyQuestions = (rawText: string) =>
  postAi<CompanyQuestionsResult>("/self-introduction/parse-questions", { raw_text: rawText });

export const critiqueSelfIntroduction = (content: string, job: string, techSummary: string) =>
  postAi<SelfIntroductionCritiqueResult>("/self-introduction/critique", { content, job, tech_summary: techSummary });

export const fetchProjectQuestions = () => getAi<{ questions: string[] }>("/project/questions");

export const generateProjectDraft = (title: string, job: string, techSummary: string, answers: string[]) =>
  postAi<ProjectDraftResult>("/project/generate", { title, job, tech_summary: techSummary, answers });

export const critiqueProject = (
  fields: { roleDescription: string; problemDescription: string; solutionDescription: string; resultDescription: string },
  job: string,
  techSummary: string,
) =>
  postAi<ProjectCritiqueResult>("/project/critique", {
    role_description: fields.roleDescription,
    problem_description: fields.problemDescription,
    solution_description: fields.solutionDescription,
    result_description: fields.resultDescription,
    job,
    tech_summary: techSummary,
  });
