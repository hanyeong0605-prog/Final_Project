import type { AnswerAnalysis } from "../model/mockInterview.types";

// Spring 백엔드(VITE_API_BASE_URL)가 아니라 파이썬 ai-server로 보낸다. 다만 브라우저가
// 직접 8001로 쏘지 않고, Vite dev 서버의 /ai-api 프록시(vite.config.ts)를 거친다 -
// 그래야 폰으로 ngrok 터널 하나만 열어도 되고 CORS도 신경 쓸 필요가 없어진다.
export async function analyzeAnswer(audioBlob: Blob, fileName: string): Promise<AnswerAnalysis> {
  const formData = new FormData();
  formData.append("audio", audioBlob, fileName);

  const response = await fetch("/ai-api/interview/analyze-answer", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `분석 요청 실패 (HTTP ${response.status})`);
  }

  return response.json();
}
