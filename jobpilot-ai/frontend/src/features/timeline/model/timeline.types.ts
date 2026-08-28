// 2026-08-10: 개인 타임라인 기능(태스크 #66~68) - Spring InterviewSessionRecord* DTO와
// 대응되는 camelCase 타입들. httpClient(Spring 백엔드)로 직접 주고받는 유일한 mock-interview
// 관련 타입이라 mockInterview.types.ts(ai-server snake_case)와 분리했다.
import type { FaceMetrics } from "../../mock-interview/lib/faceAnalysis";

export interface InterviewQuestionFeedbackInput {
  question: string;
  feedback: string;
  modelAnswer: string | null;
  // 2026-08-26: 세션 저장 시 질문별 얼굴 지표를 함께 실어서 DB(questions JSON 컬럼)에
  // 남긴다 - 카메라 미사용/인식 실패면 null.
  faceMetrics?: FaceMetrics | null;
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
  // 2026-08-29: 비언어 행동 리뷰. 카메라를 안 썼거나 분석 신뢰도가 부족하면 ai-server가
  // null을 주므로 선택 필드다(백엔드 컬럼도 nullable).
  nonverbalFeedback?: string | null;
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
  // 이 기능 이전에 저장된 기록은 null이다.
  nonverbalFeedback: string | null;
}
