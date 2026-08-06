// ai-server(app/domain/interview/audio_analysis.py)의 VoiceMetrics.to_dict()와
// 그대로 대응되는 필드명(snake_case)을 유지한다 - Python 응답을 그대로 받아쓰는
// 유일한 API라 굳이 camelCase로 변환하는 계층을 하나 더 두지 않았다.
export interface VoiceMetrics {
  duration_sec: number;
  speaking_rate_chars_per_min: number | null;
  pitch_mean_hz: number | null;
  pitch_variation_hz: number | null;
  silence_ratio: number;
  long_pause_count: number;
  volume_mean_rms: number;
  volume_variation_rms: number;
}

export interface AnswerAnalysis {
  transcript: string;
  // 2026-08-05: ai-server가 whisper 세그먼트 avg_logprob 기준으로 판단한 참고 신호 -
  // true면 인식 결과가 불안정했을 수 있다는 뜻(확정적인 "틀렸다" 판정은 아님).
  low_confidence_transcript: boolean;
  // 2026-08-05: 마이크 없이 텍스트로 답변하는 경로에서는 녹음 자체가 없어서 음성 지표가
  // 없다 - 이 경우 null.
  metrics: VoiceMetrics | null;
}

export interface NextQuestionResponse {
  question: string;
}

// ai-server evaluation.py의 EvaluationReport.to_dict()와 그대로 대응되는 필드명(snake_case).
// ok=false면 message만 의미 있고 나머지는 비어있다(키 없음/생성 실패 안내용).
export interface EvaluationReport {
  ok: boolean;
  message: string | null;
  overall_score: number | null;
  content_score: number | null;
  delivery_score: number | null;
  strengths: string[];
  improvements: string[];
  model_answer: string | null;
  next_steps: string[];
}

export interface EvaluateReportResponse {
  report: EvaluationReport;
}

// 2026-08-05: 질문마다 EvaluationReport를 따로 받던 걸 세션(보통 3개) 전체를 한 번에
// 평가하는 방식으로 바꿨다 - ai-server evaluation.py의 SessionEvaluationReport.to_dict()와
// 대응. model_answer(질문 1개짜리 필드) 대신 질문별 피드백 배열(questions)을 둔다.
export interface QuestionFeedback {
  question: string;
  feedback: string;
  model_answer: string | null;
}

export interface SessionEvaluationReport {
  ok: boolean;
  message: string | null;
  overall_score: number | null;
  content_score: number | null;
  delivery_score: number | null;
  strengths: string[];
  improvements: string[];
  next_steps: string[];
  questions: QuestionFeedback[];
}

export interface EvaluateSessionResponse {
  report: SessionEvaluationReport;
}

// 2026-08-06: ai-server tts.py의 VoiceOption.to_dict()와 대응 - 질문 낭독에 쓸 클라우드 TTS
// 음성 목록. label만 화면에 보여주고 id는 /tts 호출 시 그대로 넘긴다.
export interface TtsVoiceOption {
  id: string;
  label: string;
  gender: "FEMALE" | "MALE";
}

export interface TtsVoicesResponse {
  voices: TtsVoiceOption[];
  default: string;
}
