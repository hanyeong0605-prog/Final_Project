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
  suggested_navigate_to: string | null;
  // suggested_navigate_to가 살아남았을 때만 채워지는 이동 전 미리보기 정보
  // (ai-server site_map.SitePage). 경로가 사이트 페이지 목록에 없으면 둘 다 null이다.
  suggested_page: AssistantSuggestedPage | null;
  job_references: AssistantJobReference[];
}

export interface AssistantSuggestedPage {
  path: string;
  name: string;
  description: string;
  highlights: string[];
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
