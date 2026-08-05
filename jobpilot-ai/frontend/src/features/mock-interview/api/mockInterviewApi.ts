import type { FaceMetrics } from "../lib/faceAnalysis";
import type {
  AnswerAnalysis,
  EvaluateReportResponse,
  EvaluateSessionResponse,
  NextQuestionResponse,
  VoiceMetrics,
} from "../model/mockInterview.types";

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

// 2026-08-05: /analyze-answer(음성 분석)와 별개 호출이다 - 얼굴 지표는 브라우저에서 답변이
// 끝난 뒤 summarizeFaceFrames로 계산되므로, analyzeAnswer가 끝난 다음에야 이 함수를 부를 수
// 있다(evaluation.py router.py의 2단계 흐름 설계 메모 참고). face_metrics는 카메라를 안 썼거나
// 인식에 실패하면 null일 수 있다 - ai-server 쪽도 선택값으로 받는다.
// voiceMetrics도 마이크 없이 텍스트로 답변한 경우 null일 수 있다(같은 이유).
export async function evaluateAnswer(
  question: string,
  transcript: string,
  voiceMetrics: VoiceMetrics | null,
  faceMetrics: FaceMetrics | null,
): Promise<EvaluateReportResponse> {
  const response = await fetch("/ai-api/interview/evaluate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      question,
      transcript,
      voice_metrics: voiceMetrics,
      face_metrics: faceMetrics,
    }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `종합 평가 요청 실패 (HTTP ${response.status})`);
  }

  return response.json();
}

// 2026-08-05: 질문마다 evaluateAnswer를 부르던 걸(세션당 최대 3회 Gemini 호출) 세션이 끝난
// 뒤 한 번만 부르도록 바꿨다 - ai-server /evaluate-session, generate_session_report 참고.
// answers는 세션 진행 순서 그대로(자기소개 포함 보통 3개) 넘기면 된다.
export async function evaluateSession(
  answers: { question: string; transcript: string; voiceMetrics: VoiceMetrics | null; faceMetrics: FaceMetrics | null }[],
): Promise<EvaluateSessionResponse> {
  const response = await fetch("/ai-api/interview/evaluate-session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      answers: answers.map((a) => ({
        question: a.question,
        transcript: a.transcript,
        voice_metrics: a.voiceMetrics,
        face_metrics: a.faceMetrics,
      })),
    }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `종합 평가 요청 실패 (HTTP ${response.status})`);
  }

  return response.json();
}

// 2026-08-04: KoGPT2+LoRA로 학습한 질문 생성 모델 - ai-server/app/domain/interview/question_generator.py.
// 아직 배포 전이라 모델 파일이 없으면 503이 올 수 있는데, 그 경우는 호출부(MockInterviewPage)에서
// SAMPLE_QUESTIONS로 자연스럽게 폴백한다.
export async function fetchNextQuestion(job?: string, context?: string): Promise<NextQuestionResponse> {
  const response = await fetch("/ai-api/interview/next-question", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ job, context }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `질문 생성 요청 실패 (HTTP ${response.status})`);
  }

  return response.json();
}
