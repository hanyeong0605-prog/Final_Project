// 백엔드(Spring) /api/v1/members/me/self-introductions, /projects 응답과 필드명을 그대로
// 맞췄다(Jackson이 자바 camelCase 필드를 그대로 JSON key로 직렬화하므로 - careerProfile.types.ts와
// 같은 패턴).

export interface SelfIntroduction {
  id: number;
  title: string;
  content: string;
  primary: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SelfIntroductionInput {
  title: string;
  content: string;
  primary: boolean;
}

export interface Project {
  id: number;
  title: string;
  roleDescription: string | null;
  problemDescription: string | null;
  solutionDescription: string | null;
  resultDescription: string | null;
  githubUrl: string | null;
  deploymentUrl: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

export interface ProjectInput {
  title: string;
  roleDescription: string | null;
  problemDescription: string | null;
  solutionDescription: string | null;
  resultDescription: string | null;
  githubUrl: string | null;
  deploymentUrl: string | null;
  startedAt: string | null;
  endedAt: string | null;
}

// ai-server /resume/self-introduction/*, /resume/project/* 응답 - snake_case 그대로 받는다
// (ai-server는 파이썬이라 자바처럼 camelCase 자동 변환이 없음, mockInterview.types.ts의
// AnswerAnalysis 등과 같은 이유).
export interface SelfIntroductionDraftResult {
  ok: boolean;
  message: string | null;
  content: string | null;
}

export interface SelfIntroductionCritiqueResult {
  ok: boolean;
  message: string | null;
  strengths: string[];
  improvements: string[];
  revised_example: string | null;
}

export interface ProjectDraftResult {
  ok: boolean;
  message: string | null;
  role_description: string | null;
  problem_description: string | null;
  solution_description: string | null;
  result_description: string | null;
}

export interface ProjectCritiqueResult {
  ok: boolean;
  message: string | null;
  strengths: string[];
  improvements: string[];
  revised_example: string | null;
}
