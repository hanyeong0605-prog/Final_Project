import { postJson } from "../../../api/httpClient";

// 이 요청은 반드시 Spring JWT 인증을 거친다. 백엔드가 검증한 회원 ID만 내부망의
// ai-server로 전달하므로, 개인 이력서·스펙 RAG가 다른 회원 데이터와 섞일 수 없다.

export interface AssistantChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface AssistantChatResult {
  ok: boolean;
  message: string | null;
  reply: string | null;
  navigate_to: string | null;
  job_references: AssistantJobReference[];
}

export interface AssistantJobReference {
  job_posting_id: number;
  company_name: string;
  title: string;
  source_url: string;
  location: string;
  deadline_at: string | null;
  readiness_score: number;
  recommendation_level: string;
  summary_comment: string;
  missing_required_count: number;
}

export async function sendAssistantMessage(
  message: string,
  history: AssistantChatTurn[],
): Promise<AssistantChatResult> {
  return postJson<AssistantChatResult>("/api/v1/members/me/assistant/chat", { message, history });
}
