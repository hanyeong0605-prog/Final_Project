// 2026-08-10: 개인 타임라인 기능(태스크 #66~68) - Spring InterviewSessionRecord* DTO와
// 대응되는 camelCase 타입들. httpClient(Spring 백엔드)로 직접 주고받는 유일한 mock-interview
// 관련 타입이라 mockInterview.types.ts(ai-server snake_case)와 분리했다.
export interface InterviewQuestionFeedbackInput {
  question: string;
  feedback: string;
  modelAnswer: string | null;
}

export interface InterviewSessionRecordInput {
  role: string | null;
  interviewMode: "camera" | "chat";
  interviewType: string | null;
  questionCount: number;
  overallScore: number | null;
  contentScore: number | null;
  deliveryScore: number | null;
  strengths: string[];
  improvements: string[];
  nextSteps: string[];
  questions: InterviewQuestionFeedbackInput[];
}

export interface InterviewSessionRecordSummary {
  id: number;
  role: string | null;
  interviewMode: string;
  interviewType: string | null;
  questionCount: number;
  overallScore: number | null;
  contentScore: number | null;
  deliveryScore: number | null;
  createdAt: string;
}

export interface InterviewSessionRecordDetail extends InterviewSessionRecordSummary {
  strengths: string[];
  improvements: string[];
  nextSteps: string[];
  questions: InterviewQuestionFeedbackInput[];
}
