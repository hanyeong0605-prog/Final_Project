import type { FaceMetrics } from "../lib/faceAnalysis";
import type {
  AnswerAnalysis,
  EvaluateReportResponse,
  EvaluateSessionResponse,
  NextQuestionResponse,
  TtsVoicesResponse,
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
// 2026-08-06: category는 ai-server QUESTION_CATEGORIES 중 하나(선택) - 결제 등급별로 난이도를
// 다르게 주는 기능(태스크: 결제/크레딧 설계)에서 이 값을 채워 넣을 예정. 지금은 안 넘기면
// 빈 문자열로 처리돼 기존과 동일하게 동작한다.
// 2026-08-06: techSummary는 회원 경력프로필(career-profile)의 기술/프로젝트 요약 - 값이 있으면
// ai-server가 LoRA 대신 Gemini로 맞춤 질문을 생성한다(router.py/question_generator.py의
// generate_personalized_question 설계 메모 참고). 프로필이 없는 사용자는 안 넘기면 기존과
// 동일하게 동작한다.
export async function fetchNextQuestion(
  job?: string,
  context?: string,
  category?: string,
  techSummary?: string,
): Promise<NextQuestionResponse> {
  const response = await fetch("/ai-api/interview/next-question", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ job, context, category, tech_summary: techSummary }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `질문 생성 요청 실패 (HTTP ${response.status})`);
  }

  return response.json();
}

// 2026-08-06: 질문 낭독용 클라우드 TTS(ai-server tts.py) - 브라우저 기본 TTS보다 자연스러운
// 음성을 골라 쓸 수 있게 추가했다. GOOGLE_TTS_API_KEY가 없는 환경에서는 503이 오는데, 그
// 경우 호출부(MockInterviewPage)가 브라우저 기본 TTS(SpeechSynthesisUtterance)로 자동
// 폴백한다 - 이 함수 자체는 실패하면 그냥 에러를 던지고, 폴백 판단은 호출부 책임이다.
export async function fetchTtsVoices(): Promise<TtsVoicesResponse> {
  const response = await fetch("/ai-api/interview/tts/voices");
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `음성 목록 조회 실패 (HTTP ${response.status})`);
  }
  return response.json();
}

// 반환값은 mp3 오디오 Blob - 호출부에서 URL.createObjectURL로 감싸 <audio>/Audio()로 재생하고,
// 다 쓴 뒤 URL.revokeObjectURL로 정리해야 한다(메모리 누수 방지).
export async function synthesizeSpeech(text: string, voice: string): Promise<Blob> {
  const response = await fetch("/ai-api/interview/tts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, voice }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.detail ?? `TTS 요청 실패 (HTTP ${response.status})`);
  }

  return response.blob();
}
