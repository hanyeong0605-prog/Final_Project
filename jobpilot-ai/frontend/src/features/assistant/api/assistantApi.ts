// resumeAiApi.ts와 같은 이유로 Spring 백엔드가 아니라 파이썬 ai-server로 직접 보낸다
// (/ai-api 프록시 - vite.config.ts). 이 챗봇은 저장할 게 없어서(대화 기록은 위젯이 메모리
// 상태로만 들고 있음) Spring 쪽 CRUD가 아예 없다.

export interface AssistantChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface AssistantChatResult {
  ok: boolean;
  message: string | null;
  reply: string | null;
  navigate_to: string | null;
}

export async function sendAssistantMessage(
  message: string,
  history: AssistantChatTurn[],
): Promise<AssistantChatResult> {
  const response = await fetch("/ai-api/assistant/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message, history }),
  });
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(error?.detail ?? `요청 실패 (HTTP ${response.status})`);
  }
  return response.json();
}
