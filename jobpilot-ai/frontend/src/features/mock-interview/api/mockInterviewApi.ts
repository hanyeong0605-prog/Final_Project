import type { FaceMetrics } from "../lib/faceAnalysis";
import type { InterviewKind, InterviewQuestionSource } from "../model/interviewConfig";
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
  jobPostingId?: number,
): Promise<EvaluateReportResponse> {
  const response = await fetch("/ai-api/interview/evaluate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      question,
      transcript,
      voice_metrics: voiceMetrics,
      face_metrics: faceMetrics,
      job_posting_id: jobPostingId,
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
  jobPostingId?: number,
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
      job_posting_id: jobPostingId,
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
// 2026-08-07: angleHint는 세션 안에서 같은 job/tech_summary로 여러 번 호출할 때(질문
// 개수만큼) tech_summary가 짧고 구체적인 경우 매번 같은 소재로 질문이 수렴하는 걸 막기
// 위한 값 - MockInterviewPage.tsx buildSessionQuestions가 TECH_QUESTION_ANGLES를
// 순환시키며 채워 보낸다. 안 넘기면 서버가 기존처럼 느슨한 다양성 지시만 적용한다.
// 2026-08-25: 무료/유료 등급 분기 추가 - corpusOnly가 true면 서버가 Gemini를 아예 안 부르고
// AI Hub 코퍼스에서만 뽑는다(무료 등급 전용 경로). exclude는 세션에서 이미 나온 질문
// 텍스트 목록 - 코퍼스 폴백/전용 경로에서 중복을 피하는 데 쓴다(router.py NextQuestionRequest
// 설계 메모 참고). 옵션이 늘어나서 위치 인자 대신 객체 하나로 받는다.
// 2026-08-26: jobPostingId 추가 - 사용자가 "이 공고로 준비하기"를 선택했을 때만 채워지는
// 선택값(RAG). 무료 경로일 땐 서버가 이 값을 아예 안 보므로 굳이 안 넘겨도 무방하지만,
// 어차피 없으면 무시되니 그냥 항상 같이 보내도 안전하다.
// 2026-08-29: corpusOnly(불리언) 대신 mode/source를 명시적으로 보낸다 - ai-server가 무료
// 경로와 실전 경로를 서버에서도 갈라놓기 때문에(router.py NextQuestionRequest 참고), 어느
// 경로인지는 프론트가 매번 정해서 알려줘야 한다. mode를 필수로 둔 건 의도적이다: 서버는
// mode가 없으면 무료로 간주하므로, 안 적으면 실전 요청이 조용히 코퍼스 질문으로 바뀐다.
// source는 실전에서 질문 근거를 고르는 값이고(spec/spec_company/company), 근거에 필요한
// ID(memberId/jobPostingId)가 빠지면 서버가 400으로 돌려준다.
export async function fetchNextQuestion(options: {
  mode: InterviewKind;
  source?: InterviewQuestionSource;
  job?: string;
  context?: string;
  category?: string;
  techSummary?: string;
  angleHint?: string;
  exclude?: string[];
  jobPostingId?: number;
  memberId?: number;
}): Promise<NextQuestionResponse> {
  const response = await fetch("/ai-api/interview/next-question", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      mode: options.mode,
      source: options.source,
      job: options.job,
      context: options.context,
      category: options.category,
      tech_summary: options.techSummary,
      angle_hint: options.angleHint,
      exclude: options.exclude ?? [],
      job_posting_id: options.jobPostingId,
      member_id: options.memberId,
    }),
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
