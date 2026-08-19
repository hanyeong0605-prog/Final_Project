import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import type { ReactNode } from "react";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  Camera,
  CheckCircle2,
  Clock,
  Eye,
  Gauge,
  Lightbulb,
  LoaderCircle,
  MessageCircle,
  MessageSquareWarning,
  Mic,
  Move,
  PauseCircle,
  Quote,
  RotateCcw,
  SkipForward,
  Sparkles,
  Square,
  Volume2,
  VolumeX,
  Waves,
} from "lucide-react";
import { analyzeAnswer, evaluateSession, fetchNextQuestion, fetchTtsVoices, synthesizeSpeech } from "../features/mock-interview/api/mockInterviewApi";
import { getCareerProfile } from "../features/profile/api/careerProfileApi";
import { getSubscriptionStatus } from "../features/subscription/api/subscriptionApi";
import { saveInterviewSessionRecord } from "../features/timeline/api/timelineApi";
import { FACE_OVAL_INDICES, loadFaceLandmarker, sampleFrame, summarizeFaceFrames } from "../features/mock-interview/lib/faceAnalysis";
import type { FaceFrameSample, FaceMetrics } from "../features/mock-interview/lib/faceAnalysis";
import type { AnswerAnalysis, SessionEvaluationReport, TtsVoiceOption, VoiceMetrics } from "../features/mock-interview/model/mockInterview.types";
import { PageHeading } from "../shared/components/PageHeading";
import { RangeGauge } from "../shared/components/RangeGauge";
import { PhoneCameraPairingPanel } from "../features/mock-interview/components/PhoneCameraPairingPanel";
import { VoiceTimelineChart } from "../features/mock-interview/components/VoiceTimelineChart";

// 2026-08-04: KoGPT2+LoRA 질문 생성 모델(ai-server /interview/next-question)이 실제 질문을
// 만들어준다. 이 배열은 이제 "기본값"이 아니라 폴백용 - 모델 서버가 아직 안 떠 있거나
// (503) 네트워크 오류가 나면 여기서 하나를 대신 보여준다.
const SELF_INTRO_QUESTION = "간단하게 자기소개 부탁드립니다.";

const SAMPLE_QUESTIONS = [
  SELF_INTRO_QUESTION,
  "이 직무에 지원하신 동기가 궁금합니다.",
  "가장 기억에 남는 프로젝트 경험을 말씀해 주세요.",
  "본인의 강점과 약점은 무엇인가요?",
  "협업 중 갈등을 해결했던 경험이 있나요?",
];

// 2026-08-06: q3 폴백에서 q2만 제외하고 SELF_INTRO_QUESTION은 안 빼놨더니, API 호출이
// 둘 다 실패한 경우 SAMPLE_QUESTIONS[0](자기소개)가 무작위로 다시 뽑혀서 세션 마지막
// 질문에 "간단하게 자기소개 부탁드립니다"가 또 나오는 버그가 있었다 - 항상 자기소개는
// 제외 목록에 넣어야 해서 exclude를 배열로 받게 바꿨다.
function pickFallbackQuestion(...exclude: string[]): string {
  const excludeSet = new Set([SELF_INTRO_QUESTION, ...exclude]);
  const others = SAMPLE_QUESTIONS.filter((q) => !excludeSet.has(q));
  return others[Math.floor(Math.random() * others.length)] ?? SAMPLE_QUESTIONS[1];
}

// 2026-08-05: "break"(휴식)와 "countdown"(질문 공개 직전) 두 단계에서 똑같은 원형 타이머를
// 색만 다르게 재사용한다 - 순수 표시용이라 훅 없이 일반 함수 컴포넌트로 둔다.
function CountdownRing({ value, total, color }: { value: number; total: number; color: string }) {
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const progress = Math.max(0, Math.min(1, value / total));
  return (
    <svg width="120" height="120" viewBox="0 0 120 120">
      <circle cx="60" cy="60" r={radius} fill="none" stroke="#eef0f6" strokeWidth="8" />
      <circle
        cx="60"
        cy="60"
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth="8"
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={circumference * (1 - progress)}
        transform="rotate(-90 60 60)"
        style={{ transition: "stroke-dashoffset 1s linear" }}
      />
      <text x="60" y="70" textAnchor="middle" fontSize="34" fontWeight={700} fill="#293349">
        {value > 0 ? value : "시작!"}
      </text>
    </svg>
  );
}

// 2026-08-06: "질문 준비 중" 로딩 표시가 밋밋하다는 피드백으로 처음엔 SVG로 직접 그린
// 캐릭터를 넣었다가, 매(Falcon) 마스코트 이미지로 바꿨었다.
// 2026-08-13: 사이트 전체에 쓰기로 한 정식 고양이 마스코트("잡아드림" 캐릭터, 6가지 포즈
// 시트를 사용자가 붙여넣어줌)로 다시 교체 - 매 캐릭터는 폐기했다. 6개 포즈 중 "면접 모드"
// 라벨이 붙어있던 포즈(말풍선+마이크 아이콘)를 이 페이지 전용 마스코트로 채택해서
// 로딩 표시와 아래 녹화 화면의 "AI 면접관" 아바타에 동일하게 재사용한다 - 파이썬 PIL로
// 원본 시트에서 배경을 투명 처리해 오려낸 뒤 public/mascot-interview.png로 저장해뒀다.
// 정지 이미지라 눈 깜빡임 대신 위아래로 통통 튀는 동작에 살짝 좌우로 갸웃거리는 동작을
// 더해 생동감을 줬다(styles.css의 loading-buddy-* 참고).
function LoadingBuddy({ size = 40 }: { size?: number }) {
  return (
    <span className="loading-buddy" style={{ display: "inline-block" }}>
      <img src="/mascot-interview.png" alt="" width={size} height={(size * 372) / 496} style={{ display: "block" }} />
    </span>
  );
}

// 2026-08-05: 실제 면접처럼 "시작하기 -> 질문 3개(자기소개 포함) 준비 -> 마이크/캠 확인 ->
// 카운트다운 -> 질문 공개 & 답변"으로 이어지는 하나의 세션 흐름으로 개편했다. 예전에는
// 페이지에 들어오자마자 질문이 바로 보이고 그때그때 "다른 질문"으로 하나씩 새로 받는
// 방식이었는데, 그러면 질문을 미리 다 보고 준비할 수 있어서 실전 느낌이 안 났다 -
// 이제는 시작 버튼을 누르기 전까지 질문이 뭔지 전혀 알 수 없고, 카운트다운이 끝나는
// 순간에만 공개된다.
type Stage =
  | "start" // 랜딩 화면 - "모의면접 시작하기" 버튼만
  // 2026-08-06: "질문 생성 중" 전용 대기 화면은 없앴다 - startSession 주석 참고. 질문 3개
  // (자기소개 1번 고정)는 device-check 진입과 동시에 백그라운드에서 생성된다.
  | "device-check" // 마이크/캠 테스트로 넘어가거나 타이핑 모드 선택(질문은 백그라운드 생성 중)
  | "preparing"
  | "testing-mic"
  | "break" // 2026-08-05: 답변 사이 10초 휴식 - 얼굴 추적 루프 자체를 꺼둬서 이 시간이
  // 분석에 절대 섞이지 않게 한다(카메라 미리보기도 숨김). countdown과 별도 단계로 분리.
  | "countdown" // 질문 공개 직전 3초 카운트다운 - 여기서부터 다시 카메라/얼굴 추적 재개
  | "get-ready"
  | "recording"
  | "analyzing"
  | "session-report" // 세션의 마지막 질문까지 다 끝난 뒤 - 전체를 한 번에 종합 평가한 최종 화면
  | "error"
  | "typing";

// 2026-08-04: 숫자만 던져주면 "그래서 좋은 거야 나쁜 거야?"가 안 남는다는 피드백을
// 받고, 신뢰할 수 있는 출처가 있는 지표(말속도/침묵)에만 참고 범위 해설을 붙였다.
// 피치 변동폭·음량 떨림처럼 사람마다 평소 편차가 커서 절대 기준을 대는 게 오히려
// 애매한 판단이 되는 지표는 hint를 안 넣고 "기준 없음"으로 정직하게 남겨둔다
// (감정/긴장도를 판독하지 않는다는 이 페이지의 원래 원칙과 같은 이유).
// 2026-08-04: 위 "3분 스피치 권장 분량" 환산치는 근거가 약해서, AI Hub 채용면접 인터뷰
// 데이터(TL_05, ICT/신입, 실제 답변 1753건)의 텍스트 길이/답변 duration으로 실측한
// 분당 글자 수 분포로 교체했다 - mean 245.7, stdev 38.5, IQR(25~75%ile) 220.3~271.4자/분.
// IQR을 "일반적인 범위"로 채택(중앙값 근처 절반이 실제로 이 구간에 있었다는 뜻이라 사분위수가
// 표준편차보다 해석하기 쉬움). 계산 스크립트: ai-server 쪽에 두지 않고 1회성으로 돌린 것이라
// 별도 파일은 없음 - 필요하면 interview_qa_pairs.jsonl 만들 때 쓴 것과 같은 방식으로 재현 가능.
const SPEAKING_RATE_MIN = 220;
const SPEAKING_RATE_MAX = 271;

// 2026-08-12: VoiceMetrics에 timeline_* 시계열(배열) 필드가 추가되면서 keyof VoiceMetrics를
// 그대로 쓰면 value 타입이 number 외에 배열까지 섞여서 format/gauge/hint(전부 number 전용)가
// 타입 에러가 난다 - 이 카드 목록은 원래도 숫자 하나짜리 지표만 다루므로 시계열 필드는 제외한다
// (시계열은 VoiceTimelineChart.tsx가 별도로 그린다).
type ScalarVoiceMetricKey = Exclude<keyof VoiceMetrics, "timeline_seconds" | "timeline_pitch_hz" | "timeline_volume_rms">;

const metricLabels: {
  key: ScalarVoiceMetricKey;
  label: string;
  format: (value: number) => string;
  hint?: (value: number) => string;
  noBaseline?: boolean;
  // 2026-08-05: "정상 범위" 기준이 있는 지표에만 게이지 바를 그린다 - 값 자체의 단위로
  // min/max(막대 전체 스케일)와 goodMin/goodMax(양호 구간)를 넣는다. 기준이 없는 지표
  // (noBaseline: true인 것들)는 애초에 "정상 구간"이라는 게 없어서 게이지를 안 그린다.
  gauge?: { min: number; max: number; goodMin: number; goodMax: number };
  // 2026-08-05: 대시보드 metric-card 톤 시스템(색+아이콘)을 지표 성격별로 매핑 - 시간/속도는
  // blue, 파형·떨림처럼 기준 없는 원시 신호는 purple, 침묵·주의가 필요할 수 있는 지표는 orange.
  icon: ReactNode;
  tone: "blue" | "orange" | "purple" | "green";
}[] = [
  { key: "duration_sec", label: "답변 길이", format: (v) => `${v.toFixed(1)}초`, icon: <Clock />, tone: "blue" },
  {
    key: "speaking_rate_chars_per_min",
    label: "말속도",
    format: (v) => `분당 ${v.toFixed(0)}자`,
    hint: (v) =>
      v < SPEAKING_RATE_MIN
        ? `일반적인 권장 속도(분당 ${SPEAKING_RATE_MIN}~${SPEAKING_RATE_MAX}자)보다 느린 편이에요.`
        : v > SPEAKING_RATE_MAX
          ? `일반적인 권장 속도(분당 ${SPEAKING_RATE_MIN}~${SPEAKING_RATE_MAX}자)보다 빠른 편이에요.`
          : "일반적으로 권장되는 속도 범위예요.",
    // 실측 분포(IQR 220.3~271.4)보다 위아래로 넉넉하게 잡아서 대부분의 실제 값이 막대
    // 안쪽에 자연스럽게 들어오게 했다(너무 좁으면 막대 양 끝에 값이 몰려 보임).
    gauge: { min: 100, max: 350, goodMin: SPEAKING_RATE_MIN, goodMax: SPEAKING_RATE_MAX },
    icon: <Gauge />,
    tone: "purple",
  },
  { key: "pitch_mean_hz", label: "평균 음높이", format: (v) => `${v.toFixed(0)}Hz`, icon: <Waves />, tone: "blue" },
  {
    key: "pitch_variation_hz",
    label: "음높이 변동폭",
    format: (v) => `${v.toFixed(0)}Hz`,
    noBaseline: true,
    icon: <Activity />,
    tone: "purple",
  },
  {
    key: "silence_ratio",
    label: "침묵 비율",
    format: (v) => `${(v * 100).toFixed(1)}%`,
    hint: (v) => (v * 100 > 30 ? "침묵 비율이 다소 높아요. 답변이 자주 끊겼을 수 있어요." : "적절한 수준의 침묵 비율이에요."),
    // silence_ratio는 0~1 원시값이라 게이지도 같은 단위(0~1, 양호 구간 0~0.3)로 맞췄다.
    gauge: { min: 0, max: 1, goodMin: 0, goodMax: 0.3 },
    icon: <VolumeX />,
    tone: "orange",
  },
  {
    key: "long_pause_count",
    label: "긴 침묵 횟수",
    format: (v) => `${v}회`,
    hint: (v) => (v === 0 ? "긴 침묵 없이 이어갔어요." : `${v}번 길게 끊겼어요.`),
    icon: <PauseCircle />,
    tone: "orange",
  },
  {
    key: "volume_variation_rms",
    label: "음량 떨림 정도",
    format: (v) => v.toFixed(4),
    noBaseline: true,
    icon: <Waves />,
    tone: "purple",
  },
];

// 참고 기준 자체가 없는 지표들 - 카드 아래 이 문구를 공통으로 보여준다.
const NO_BASELINE_HINT = "비교 기준 없음 - 여러 번 연습해서 평소 값과 비교해 보세요.";

// blinkCount: 녹음하는 동안 실제로 센 깜빡임 횟수(그대로).
// blinkRatePerMin: 그 횟수를 "1분 동안 이 속도가 유지됐다면"으로 환산한 값 -
// 답변이 짧으면 실제 횟수보다 훨씬 커 보일 수 있어서(예: 6초에 3회 -> 분당 30회),
// 반드시 blinkCount와 나란히 보여줘서 오해가 없게 한다.
const faceMetricLabels: {
  key: keyof FaceMetrics;
  label: string;
  format: (value: number) => string;
  noBaseline?: boolean;
  icon: ReactNode;
}[] = [
  { key: "blinkCount", label: "실제 깜빡임 횟수", format: (v) => `${v}회`, icon: <Eye /> },
  { key: "blinkRatePerMin", label: "분당 깜빡임 (환산)", format: (v) => `${v}회/분`, noBaseline: true, icon: <Eye /> },
  { key: "headMovement", label: "고개 움직임 정도", format: (v) => `${v}/100`, noBaseline: true, icon: <Move /> },
];

// 표준국어대사전 기준 대표적인 구어체 습관어(필러워드). 어절(공백 기준 토큰) 단위로
// "정확히 일치"할 때만 센다 - "그"를 부분 문자열로 매칭하면 "그래서"/"그런데"까지
// 전부 걸려서 완전히 다른 결과가 나온다. 토큰 단위로 해도 "그", "저" 같은 건 원래
// 지시대명사로도 쓰이니 완벽하진 않지만(오탐 가능), 부분 매칭보다는 훨씬 정확하다.
const FILLER_WORDS = new Set(["음", "어", "그니까", "그러니까", "저기", "뭐랄까", "인제", "저"]);

function analyzeFillers(transcript: string): { count: number; parts: { text: string; isFiller: boolean }[] } {
  const tokens = transcript.split(/(\s+)/); // 공백도 캡처해서 원문 그대로 재조립
  let count = 0;
  const parts = tokens.map((token) => {
    const trimmed = token.replace(/[.,!?~…]+$/, "");
    const isFiller = FILLER_WORDS.has(trimmed) && trimmed.length > 0;
    if (isFiller) count += 1;
    return { text: token, isFiller };
  });
  return { count, parts };
}

// 규칙 기반으로 이미 계산된 숫자들을 문장으로 조립만 한다 - 새로운 판단을 더하지
// 않는다(감정/긴장도 추정 금지 원칙과 동일). 각 조건은 위 hint들과 같은 기준을 쓴다.
function buildSummarySentence(result: AnswerAnalysis, fillerCount: number): string {
  if (!result.metrics) return ""; // 타이핑으로 답변한 경우 - 음성 지표 자체가 없음
  const parts: string[] = [];
  const rate = result.metrics.speaking_rate_chars_per_min;
  if (rate !== null && rate !== undefined) {
    if (rate >= SPEAKING_RATE_MIN && rate <= SPEAKING_RATE_MAX) parts.push("말속도는 적정 범위였어요");
    else if (rate < SPEAKING_RATE_MIN) parts.push("말속도가 다소 느렸어요");
    else parts.push("말속도가 다소 빨랐어요");
  }
  if (result.metrics.long_pause_count === 0 && result.metrics.silence_ratio * 100 <= 30) {
    parts.push("끊김 없이 이어갔어요");
  } else if (result.metrics.silence_ratio * 100 > 30) {
    parts.push("침묵이 잦았어요");
  }
  if (fillerCount >= 3) parts.push(`습관어를 ${fillerCount}번 사용했어요`);
  return parts.length > 0 ? parts.join(", ") + "." : "";
}

// 2026-08-05: 질문마다 Gemini를 부르던 걸 세션이 끝난 뒤 한 번만 부르도록 바꾸면서 생긴
// 최종 화면 - answers(세션에서 쌓인 답변들)를 받아 마운트되자마자 evaluateSession을 딱
// 1번 호출하고, 로딩/완료를 자체적으로 관리한다. MockInterviewPage 본문에서 훅 개수가
// 스테이지마다 달라지는 걸 피하려고 별도 컴포넌트로 뺐다.
function SessionReportPanel({
  answers,
  onEndSession,
  role,
  interviewMode,
  interviewType,
}: {
  answers: { question: string; result: AnswerAnalysis; faceMetrics: FaceMetrics | null }[];
  onEndSession: () => void;
  // 2026-08-10: 개인 타임라인 기능(태스크 #67) - AI 분석이 끝나면 이 메타데이터와 함께
  // Spring에 저장한다. 이전엔 이 값들이 MockInterviewPage 본문 상태에만 있고
  // SessionReportPanel까지 안 내려와서, 세션이 끝난 뒤엔 "무슨 분야/유형이었는지"가 이미
  // 사라진 상태였다(리서치로 확인됨) - 그래서 props로 새로 받는다.
  role: string | null;
  interviewMode: "camera" | "chat";
  interviewType: string | null;
}) {
  const [report, setReport] = useState<SessionEvaluationReport | null>(null);
  // 2026-08-06: 원래는 이 화면에 들어오자마자 자동으로 Gemini를 호출했는데, "처음엔 지표만
  // 보여주고, 스크롤해서 아래 'AI 분석' 버튼을 눌러야 그때 모범답안/총평까지 레포트처럼
  // 한 번에 보여주면 좋겠다"는 요청으로 바꿨다 - requested가 true가 되기 전까지는
  // evaluateSession을 아예 호출하지 않는다(질문마다 지표만 먼저 훑어보고, 실제로 AI
  // 피드백이 궁금할 때만 누르라는 의도).
  const [requested, setRequested] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadReport = () => {
    setRequested(true);
    setLoading(true);
    setLoadError(null);
    evaluateSession(
      answers.map((a) => ({
        question: a.question,
        transcript: a.result.transcript,
        voiceMetrics: a.result.metrics,
        faceMetrics: a.faceMetrics,
      })),
    )
      .then((res) => {
        setReport(res.report);
        if (res.report.ok) {
          // 2026-08-10: 타임라인 저장은 부가 기능이라 실패해도 지금 보고 있는 AI 분석
          // 결과 자체에는 영향을 주면 안 된다 - 실패를 조용히 무시한다(에러를 화면에
          // 보여주면 "저장은 안 됐는데 방금 본 분석 결과도 잘못된 건가" 하는 혼란만 준다).
          void saveInterviewSessionRecord({
            role,
            interviewMode,
            interviewType,
            questionCount: answers.length,
            overallScore: res.report.overall_score,
            contentScore: res.report.content_score,
            deliveryScore: res.report.delivery_score,
            strengths: res.report.strengths,
            improvements: res.report.improvements,
            nextSteps: res.report.next_steps,
            questions: res.report.questions.map((q) => ({
              question: q.question,
              feedback: q.feedback,
              modelAnswer: q.model_answer,
            })),
          }).catch(() => {});
        }
      })
      .catch((error) => setLoadError(error instanceof Error ? error.message : "종합 평가를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  };

  return (
    <section className="panel" style={{ marginTop: 20 }}>
      <div className="panel-title">
        <div>
          <h2>면접 결과</h2>
          <p>질문 {answers.length}개에 대한 답변 지표입니다. 아래 "AI 분석"을 누르면 전체를 종합 평가한 리포트를 볼 수 있어요.</p>
        </div>
        <button className="text-button" onClick={onEndSession} type="button">
          <RotateCcw size={13} /> 처음으로
        </button>
      </div>

      {answers.map((a, i) => {
        const { count: fillerCount, parts: fillerParts } = analyzeFillers(a.result.transcript ?? "");
        const summary = buildSummarySentence(a.result, fillerCount);
        return (
          <div key={i} style={{ marginTop: 24, paddingTop: 20, borderTop: "1px solid #eef0f4" }}>
            <div className="interview-question-card" style={{ marginBottom: 16 }}>
              <span className="interview-question-icon">
                <Sparkles size={19} />
              </span>
              <strong>{`Q${i + 1}. ${a.question}`}</strong>
            </div>

            {summary && (
              <div className="interview-summary-banner">
                <Sparkles size={16} />
                {summary}
              </div>
            )}

            <div className="interview-transcript-box" style={{ marginBottom: 20 }}>
              <span className="interview-field-label">{a.result.metrics ? "인식된 답변" : "답변 내용"}</span>
              {a.result.low_confidence_transcript && (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 6,
                    margin: "8px 0 0",
                    padding: "6px 10px",
                    borderRadius: 8,
                    background: "#fff4e5",
                    color: "#a05a00",
                    fontSize: 11,
                  }}
                >
                  <AlertCircle size={12} />
                  인식이 불안정했을 수 있어요 - 아래 텍스트가 실제 답변과 다를 수 있습니다. (배경 소음이 있었거나 마이크와 거리가 멀었을 때 자주 발생해요)
                </div>
              )}
              <p style={{ margin: "8px 0 0", color: "#293349", fontSize: 13, lineHeight: 1.6 }}>
                {a.result.transcript
                  ? fillerParts.map((part, pi) =>
                      part.isFiller ? (
                        <mark key={pi} style={{ background: "#ffe6a8", borderRadius: 3, padding: "0 2px" }}>
                          {part.text}
                        </mark>
                      ) : (
                        <span key={pi}>{part.text}</span>
                      ),
                    )
                  : "(인식된 내용 없음)"}
              </p>
              {fillerCount > 0 && (
                <span className="analysis-muted" style={{ fontSize: 11 }}>
                  습관어("음", "어", "그니까" 등) {fillerCount}회 감지됨 (형광 표시)
                </span>
              )}
            </div>

            <div className="metric-grid interview-metric-grid" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(190px, 1fr))" }}>
              {a.result.metrics &&
                metricLabels.map(({ key, label, format, hint, noBaseline, gauge, icon, tone }) => {
                  const value = a.result.metrics![key];
                  const hasValue = value !== null && value !== undefined;
                  return (
                    <div key={key} className={`metric-card ${tone}`} style={{ alignItems: "flex-start" }}>
                      <span className="metric-icon">{icon}</span>
                      <div>
                        <span>{label}</span>
                        <strong>{hasValue ? format(value) : "-"}</strong>
                        {hasValue && gauge && <RangeGauge value={value} {...gauge} />}
                        {hasValue && hint && <small style={{ whiteSpace: "normal" }}>{hint(value)}</small>}
                        {hasValue && !hint && noBaseline && <small style={{ whiteSpace: "normal" }}>{NO_BASELINE_HINT}</small>}
                      </div>
                    </div>
                  );
                })}
              {a.result.transcript && (
                <div className="metric-card orange">
                  <span className="metric-icon">
                    <MessageSquareWarning />
                  </span>
                  <div>
                    <span>습관어 사용 횟수</span>
                    <strong>{fillerCount}회</strong>
                  </div>
                </div>
              )}
              {a.faceMetrics &&
                faceMetricLabels.map(({ key, label, format, noBaseline, icon }) => (
                  <div key={key} className="metric-card green" style={{ alignItems: "flex-start" }}>
                    <span className="metric-icon">{icon}</span>
                    <div>
                      <span>{label}</span>
                      <strong>{format(a.faceMetrics![key] as number)}</strong>
                      {noBaseline && <small style={{ whiteSpace: "normal" }}>{NO_BASELINE_HINT}</small>}
                    </div>
                  </div>
                ))}
            </div>

            {a.result.metrics && <VoiceTimelineChart metrics={a.result.metrics} />}

            {a.result.metrics && !a.faceMetrics && (
              <p className="analysis-muted" style={{ marginTop: 12, fontSize: 11 }}>
                얼굴이 인식되지 않아 표정 관련 지표는 계산되지 않았습니다. 카메라 각도를 조정하고 다시 시도해 보세요.
              </p>
            )}

          </div>
        );
      })}

      {/* 2026-08-06: 지표를 먼저 다 훑어본 다음, 맨 아래에서 "AI 분석"을 눌러야 총평/강점/
          개선점/질문별 모범답안이 레포트 형태로 한 번에 나온다(요청 반영) - 누르기 전엔
          Gemini를 호출하지 않는다. */}
      <div className="interview-report-panel" style={{ marginTop: 24 }}>
        <div className="interview-report-head">
          <Sparkles size={16} /> AI 종합 평가
        </div>
        {!requested && (
          <button className="primary-button" onClick={loadReport} type="button">
            <Sparkles size={14} /> AI 분석 보기
          </button>
        )}
        {loading && (
          <p style={{ display: "flex", alignItems: "center", gap: 8, margin: 0, color: "#6a7383", fontSize: 13 }}>
            <LoaderCircle className="spin" size={14} /> 면접 전체를 종합 평가하는 중입니다...
          </p>
        )}
        {!loading && requested && (loadError || (report && !report.ok)) && (
          <p style={{ margin: 0, color: "#293349", fontSize: 13, lineHeight: 1.7 }}>{loadError ?? report?.message}</p>
        )}
        {!loading && report && report.ok && (
          <>
            <div className="interview-score-row">
              {[
                { label: "총평", value: report.overall_score },
                { label: "답변 내용", value: report.content_score },
                { label: "전달력", value: report.delivery_score },
              ]
                .filter((s): s is { label: string; value: number } => s.value !== null)
                .map(({ label, value }) => {
                  // 2026-08-10: 점수를 1~5점에서 100점 만점으로 바꾸면서 등급 기준도 비례
                  // 조정(4/5=80%, 3/5=60% 그대로 유지) - evaluation.py _clamp_score 참고.
                  const tone = value >= 80 ? "score-high" : value >= 60 ? "score-mid" : "score-low";
                  return (
                    <div key={label} className={`interview-score-card ${tone}`}>
                      <span className="interview-score-ring">{value}</span>
                      <div>
                        <span className="interview-score-label">{label}</span>
                        <strong>{value} / 100</strong>
                      </div>
                    </div>
                  );
                })}
            </div>

            {report.strengths.length > 0 && (
              <div className="interview-report-section">
                <h4>
                  <CheckCircle2 size={14} color="#37bf82" /> 잘한 점
                </h4>
                <ul className="interview-report-list strengths">
                  {report.strengths.map((item, i) => (
                    <li key={i}>
                      <CheckCircle2 size={14} />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {report.improvements.length > 0 && (
              <div className="interview-report-section">
                <h4>
                  <AlertTriangle size={14} color="#e0a233" /> 개선할 점
                </h4>
                <ul className="interview-report-list improvements">
                  {report.improvements.map((item, i) => (
                    <li key={i}>
                      <AlertTriangle size={14} />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {report.next_steps.length > 0 && (
              <div className="interview-report-section" style={{ marginBottom: 0 }}>
                <h4>
                  <Lightbulb size={14} color="#6678e8" /> 다음에 연습하면 좋을 점
                </h4>
                <ul className="interview-report-list next-steps">
                  {report.next_steps.map((item, i) => (
                    <li key={i}>
                      <Lightbulb size={14} />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* 2026-08-06: 원래 질문 카드마다 따로 붙어 있던 걸 여기로 모았다 - "총평 보려고
                내려왔다가 질문별 피드백 보려고 다시 위로 올라가야 하는" 왔다갔다를 없애려고,
                AI 관련 내용은 전부 이 패널 하나에 모아서 보여준다. 위 섹션들과 붙어 보인다는
                피드백을 받아서 구분선+여백을 확실히 주고, 질문 하나당 카드로 나눴다 - 피드백/
                모범답안이 중요한 정보라 옅은 배경색 대신 기존 .interview-model-answer(파란
                왼쪽 테두리 + 그라데이션 배경)를 그대로 재사용해서 확실히 눈에 띄게 했다. */}
            {report.questions.length > 0 && (
              <div style={{ marginTop: 26, paddingTop: 22, borderTop: "1px solid #eef0f4" }}>
                <div className="interview-report-head" style={{ marginBottom: 14 }}>
                  <Quote size={16} /> 질문별 피드백
                </div>
                <div style={{ display: "grid", gap: 16 }}>
                  {report.questions.map((q, i) => (
                    <div key={i} style={{ border: "1px solid #e8ebf1", borderRadius: 12, background: "#fbfcff", padding: "16px 18px" }}>
                      <strong style={{ display: "block", color: "#293349", fontSize: 13, marginBottom: 10 }}>
                        {`Q${i + 1}. ${q.question}`}
                      </strong>
                      {q.feedback && <p style={{ margin: 0, color: "#3a4356", fontSize: 13, lineHeight: 1.7 }}>{q.feedback}</p>}
                      {q.model_answer && (
                        <div className="interview-model-answer" style={{ marginTop: 12, marginBottom: 0 }}>
                          <h4>
                            <Quote size={13} /> 모범 답안 예시
                          </h4>
                          <p>{q.model_answer}</p>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}

export function MockInterviewPage() {
  const [question, setQuestion] = useState("");
  const [stage, setStage] = useState<Stage>("start");
  // 2026-08-05: 질문마다 즉시 결과 화면을 보여주던 걸 없애고, 세션의 답변을 여기 계속
  // 쌓아뒀다가 마지막 질문까지 끝나면 한 번에 SessionReportPanel에 넘겨서 보여준다.
  const [sessionAnswers, setSessionAnswers] = useState<{ question: string; result: AnswerAnalysis; faceMetrics: FaceMetrics | null }[]>(
    [],
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [micLevel, setMicLevel] = useState(0);
  const [cameraReady, setCameraReady] = useState(false);
  const [pairingPanelOpen, setPairingPanelOpen] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  // 2026-08-05: 마이크/카메라를 못 쓰거나 쓰기 부담스러운 사람을 위한 보조 경로 - 주 기능은
  // 여전히 녹음이라(device-check 화면에서 아주 작은 글씨 링크로만 노출) answerMode는
  // "voice"가 기본값.
  const [answerMode, setAnswerMode] = useState<"voice" | "text">("voice");
  const [typedAnswer, setTypedAnswer] = useState("");
  // 2026-08-06: "채팅으로 연습하기"를 작은 팝업 위젯 대신 카메라/마이크 모드처럼 화면을
  // 통째로 바꾸는 전용 흐름으로 요청받아 추가했다 - device-check(카메라/마이크 안내)를
  // 완전히 건너뛰고 곧장 "typing" 단계로 간다. 이 플래그로 "취소"를 눌렀을 때 device-check가
  // 아니라 시작화면(start)으로 돌아가야 한다는 걸 구분한다(기존 마이크/캠 경로 안에 있던
  // "마이크/캠을 사용할 수 없어요" 보조 링크는 그대로 device-check로 돌아가야 해서 남겨뒀다).
  const [chatOnlyMode, setChatOnlyMode] = useState(false);
  // 2026-08-06: "질문도 제한시간 안에 고민 안 하고 칠 수 있게" 요청으로 추가한 타이핑 답변
  // 제한시간 - 답변을 소리 내어 말하는 것보다 타이핑은 오래 걸리는 걸 감안해 90초로 잡았다.
  // 시간이 다 되면(입력된 내용이 있을 때만) 자동 제출해서 실제 면접처럼 시간 압박을 준다.
  const TYPING_TIME_LIMIT_SEC = 90;
  const [typingSecondsLeft, setTypingSecondsLeft] = useState(TYPING_TIME_LIMIT_SEC);
  // 2026-08-05: 이번 세션에서 쓸 질문 3개(자기소개 포함, 순서 셔플됨) - "시작하기"를 누르는
  // 순간 한 번에 미리 만들어두고, 카운트다운이 끝날 때마다 순서대로 하나씩 공개한다.
  const [sessionQuestions, setSessionQuestions] = useState<string[]>([]);
  const [sessionIndex, setSessionIndex] = useState(0);
  const [countdownValue, setCountdownValue] = useState(0);
  // 2026-08-05: 첫 질문 직전엔 짧게(3초), 두 번째 질문부터는 답변을 확인하고 숨 고를
  // 시간까지 겸해서 좀 더 길게(10초) 카운트다운한다 - 원형 타이머 진행률 계산에 필요.
  const [countdownTotal, setCountdownTotal] = useState(3);
  // 2026-08-06: 질문 낭독용 클라우드 TTS 음성 선택 - 목록은 서버에서 받아오고(키가 없는
  // 환경이면 빈 배열이 와서 선택 UI 자체를 안 보여주고 브라우저 기본 TTS만 쓴다), 선택값은
  // 다음 방문에도 유지되게 localStorage에 저장한다.
  const [ttsVoiceOptions, setTtsVoiceOptions] = useState<TtsVoiceOption[]>([]);
  const [selectedTtsVoice, setSelectedTtsVoice] = useState<string>(
    () => localStorage.getItem("mockInterviewTtsVoice") ?? "",
  );
  // 2026-08-06: 회원 경력프로필(마이페이지에서 입력한 목표 직무/기술·프로젝트 요약)을 질문
  // 생성에 반영하기 위해 마운트 시 조회해둔다 - fetchNextQuestion(job, context, category,
  // techSummary)의 techSummary로 그대로 넘어간다(ai-server가 값이 있으면 Gemini 맞춤 질문
  // 경로를 탄다, question_generator.py generate_personalized_question 참고). 프로필을 아직
  // 안 입력했거나 "건너뛰기"한 사용자는 targetRole/technicalSummary가 비어있을 수 있는데,
  // 그 경우 techSummary가 빈 문자열로 넘어가서 기존 LoRA 경로가 그대로 적용된다(동작 변화
  // 없음). 조회 자체가 실패해도(비로그인 등) 그냥 빈 값 취급하고 넘어간다.
  const [careerJob, setCareerJob] = useState("");
  const [careerTechSummary, setCareerTechSummary] = useState("");
  // 2026-08-13: "프로필 불러오기는 구독자 기준이어야 하고, 연습이냐 프로필 불러오기냐를
  // 사용자가 직접 선택하게 해달라"는 요청으로 추가 - 이전엔 "면접 분야"에서 "선택 안 함"을
  // 고르면(기본값) 프로필이 있으면 조용히 자동 적용됐는데, 이제는 명시적으로 "프로필
  // 불러오기"를 골라야만 적용되고, 그마저도 구독 중(또는 관리자)일 때만 실제로 반영된다.
  // 기본값은 "연습"이라 아무것도 안 고르면 예전처럼 프로필이 몰래 섞여 들어가는 일이 없다.
  const [questionSource, setQuestionSource] = useState<"practice" | "profile">("practice");
  const [subscribed, setSubscribed] = useState(false);

  useEffect(() => {
    void getSubscriptionStatus().then((status) => setSubscribed(status.subscribed)).catch(() => setSubscribed(false));
  }, []);
  // 2026-08-06: "분야로 질문 분류 가능하지 않냐"는 요청으로 추가했다 - 처음엔 채용공고
  // 필터(AllJobPostingsPage.tsx roleOptions/백엔드 ROLE_KEYWORDS)의 9개 분류를 그대로
  // 재사용했는데, "모의면접 카드는 5개(백엔드/프론트엔드/풀스택/모바일/데이터·AI·기타)가
  // 낫겠다"는 피드백으로 면접용으로 단순화했다 - Gemini에 넘기는 job은 자유 텍스트라
  // 채용공고 쪽 enum과 굳이 1:1로 맞출 필요가 없다(QA/보안/게임·임베디드는 빈도가 낮아
  // "데이터 · AI · 기타"로 흡수). 여기서 고른 분야는 회원 프로필의 targetRole보다
  // 우선한다 - 프로필을 안 채운 사용자도 이 선택만으로 그 분야 질문을 받을 수 있다
  // (question_generator.py generate_personalized_question 참고 - job만 있어도 Gemini
  // 맞춤 질문 경로를 탄다).
  const INTERVIEW_ROLE_OPTIONS = [
    ["BACKEND", "백엔드"], ["FRONTEND", "프론트엔드"], ["FULLSTACK", "풀스택"],
    ["MOBILE", "모바일 (iOS/Android)"], ["DATA_AI", "데이터 · AI · 기타"],
  ] as const;
  // 2026-08-07: 처음부터 "선택 안 함" 칩이 파랗게 켜져 있으면 마치 사용자가 이미 뭔가 고른
  // 것처럼 보인다는 피드백으로, 초기값을 ""(선택 안 함을 명시적으로 고른 상태)이 아니라
  // null(아직 아무것도 안 고른 상태)로 분리했다 - "선택 안 함" 칩은 selectedRole === ""일
  // 때만 활성 표시되므로, 처음엔 null이라 어떤 칩도 안 켜져 있다가 사용자가 실제로 클릭해야
  // 그 칩이 켜진다. buildSessionQuestions 쪽 로직(찾아서 없으면 undefined)은 null/""
  // 둘 다 "매칭 없음"으로 동일하게 처리되므로 동작 자체는 그대로다.
  // 2026-08-10: 태스크 #39 "이 부분 연습하기" 딥링크 - TimelinePage의 누적 인사이트/세션
  // 리포트에서 "role=BACKEND&type=직무면접" 같은 쿼리로 이 페이지로 넘어오면 그 분야/유형을
  // 미리 선택해둔다(자동 시작은 안 함 - 사용자가 직접 "모의면접 시작하기"를 눌러야 함,
  // 갑자기 세션이 시작되면 당황스러우니까). 값이 옵션에 없으면(오타/구버전 링크 등) 그냥
  // 미선택 상태로 둔다.
  const [searchParams] = useSearchParams();
  const [selectedRole, setSelectedRole] = useState<string | null>(() => {
    const fromQuery = searchParams.get("role");
    return INTERVIEW_ROLE_OPTIONS.some(([code]) => code === fromQuery) ? fromQuery : null;
  });
  // 2026-08-06: 카메라/채팅 카드를 클릭해서 고르는 라디오 방식으로 바꾸면서 다시 추가 -
  // 실제 시작은 맨 아래 단일 "모의면접 시작하기" 버튼이 이 값을 보고 분기한다.
  const [interviewMode, setInterviewMode] = useState<"camera" | "chat">("camera");
  // 2026-08-06: "질문 몇 개로 할지도 카드로 고르게" 요청 - 자기소개 1개는 항상 고정이고
  // 나머지 (개수-1)개를 카테고리를 돌려가며 생성한다(buildSessionQuestions 참고).
  const QUESTION_COUNT_OPTIONS = [3, 5, 7] as const;
  const [questionCount, setQuestionCount] = useState<number>(3);

  // 2026-08-07: "역량/직무/인성 면접 유형도 고르게 하자" 요청으로 추가 - 코드/카테고리 튜플은
  // ai-server question_generator.py의 INTERVIEW_TYPES와 반드시 이름을 맞춰야 한다(카테고리
  // 문자열을 그대로 next-question 요청에 실어 보내므로). "전체"(기본값)는 6개 카테고리를
  // 다 순환하는 기존 동작 그대로 - 유형을 명시적으로 고르면 그 유형에 속한 카테고리만 순환.
  const INTERVIEW_TYPE_OPTIONS = [
    ["인성면접", ["가치관_자기관리", "협업_리더십_커뮤니케이션"]],
    ["역량면접", ["문제해결_도전경험", "강점_약점"]],
    ["직무면접", ["기술_직무역량"]],
  ] as const;
  // 2026-08-07: selectedRole과 같은 이유로 null(미선택)과 ""("전체"를 명시적으로 고른 상태)을
  // 분리했다 - 위 selectedRole 설계 메모 참고.
  const [selectedInterviewType, setSelectedInterviewType] = useState<string | null>(() => {
    const fromQuery = searchParams.get("type");
    return INTERVIEW_TYPE_OPTIONS.some(([type]) => type === fromQuery) ? fromQuery : null;
  });

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const phonePairDisconnectRef = useRef<(() => void) | null>(null);
  const phonePairStateRef = useRef<((stage: string, question?: string, elapsedSec?: number) => void) | null>(null);
  const phoneAutoStartRef = useRef(false);
  const audioContextRef = useRef<AudioContext | null>(null);
  // 2026-08-12 추가: 마이크 테스트 화면의 막대바 대신 실제 파형(오실로스코프 모양)을
  // 그려주기 위한 canvas ref - startMeterLoop의 tick()에서 매 프레임 그린다.
  const waveformCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const rafIdRef = useRef<number | null>(null);
  const faceRafIdRef = useRef<number | null>(null);
  const faceFramesRef = useRef<FaceFrameSample[]>([]);
  const isRecordingRef = useRef(false);
  const timerIdRef = useRef<number | null>(null);
  // 2026-08-05: 얼굴 인식 모델은 로딩에 몇 초 걸려서, 세션 중 다음 질문으로 넘어갈 때마다
  // 다시 로딩하지 않도록 한 번 로드한 걸 캐싱해둔다(카메라 스트림 자체도 질문 사이에 끊지
  // 않고 계속 켜둔다 - stopRecording 참고).
  const landmarkerRef = useRef<Awaited<ReturnType<typeof loadFaceLandmarker>> | null>(null);
  // 2026-08-05: 카운트다운이 끝나는 순간 공개할 질문을 미리 담아두는 곳 - sessionIndex
  // state는 비동기라 카운트다운 이펙트 안에서 타이밍 문제가 생길 수 있어서, 대신 이 ref에
  // "다음에 공개할 질문 텍스트"를 직접 넣어두고 그대로 읽는다.
  const pendingQuestionRef = useRef<string | null>(null);
  // 2026-08-05: 버그 수정 - finishAnswer가 question "state"를 직접 읽으면, 녹음 파이프라인
  // 전체(get-ready -> recording -> stop -> 분석 -> finishAnswer)가 질문이 공개된 시점의
  // 렌더에서 만들어진 클로저 체인(onend/setTimeout/recorder.onstop으로 미리 엮여있음)을
  // 그대로 쓰기 때문에, setQuestion(text)가 아직 리렌더에 반영되기 "전"의 오래된 question
  // 값을 참조해버린다(그 결과 세션 리포트에 항상 한 질문 전 텍스트, 첫 질문은 빈 문자열이
  // 붙는 버그가 있었다). ref는 .current를 어느 클로저에서 읽어도 항상 최신값이라 이 문제가
  // 없다 - 질문을 공개하는 모든 지점에서 state와 함께 이 ref도 같이 갱신한다.
  const currentQuestionRef = useRef("");
  // 2026-08-06: 답변 분석(analyzeAnswer API 호출)과 10초 쉬는 시간을 순차로 이어붙이면
  // 체감 대기시간이 "분석 시간 + 10초"로 길어져서, 녹음이 끝나자마자 분석은 백그라운드로
  // 바로 돌리고 화면엔 10초 쉬는 시간 모션만 보여주는 걸로 바꿨다(둘을 병렬로 겹침).
  // 이 두 ref는 "쉬는 시간 카운트다운이 다 끝났는지"와 "분석 결과가 준비됐는지"를 각각
  // 추적해서, 둘 다 끝난 시점(늦게 끝나는 쪽 기준)에만 다음 질문으로 넘어가게 한다.
  const breakCountdownDoneRef = useRef(false);
  const pendingAnalysisRef = useRef<{ analysis: AnswerAnalysis; faceMetrics: FaceMetrics | null } | null>(null);
  // 2026-08-06: 카운트다운(3초)이 끝나는 순간 question state는 바뀌지만, questionRevealed가
  // "get-ready" 단계부터만 true라 TTS가 질문을 다 읽는 동안엔 화면에 텍스트가 안 보이다가
  // 녹음 시작 직전에 갑자기 "팍" 나타나는 것처럼 느껴졌다 - 질문을 실제로 공개하는 시점
  // (revealQuestionAndBeginRecording)에 맞춰 이 값을 true로 켜서, TTS가 읽어주는 동안에도
  // 텍스트가 화면에 같이 보이게 한다.
  const [questionTextReady, setQuestionTextReady] = useState(false);
  // 2026-08-06: 클라우드 TTS로 재생 중인 <audio> 엘리먼트/objectURL - 새 질문을 읽기 시작할
  // 때 이전 재생을 확실히 멈추고, objectURL은 다 쓰면 revoke해서 메모리 누수를 막는다.
  const ttsAudioRef = useRef<HTMLAudioElement | null>(null);
  const ttsAudioUrlRef = useRef<string | null>(null);
  // 2026-08-13: "경청하다가 질문할 때 입 벌리는 느낌" 요청.
  // 처음엔 JS setInterval로 정지 이미지 2장을 번갈아 보여주는 방식으로 했었는데, 실제
  // 애니메이션 파일(움짤)로 만들어달라는 요청 + "고양이 크기가 통째로 움직인다"는 버그
  // 리포트를 받고 원인 파악 - 문제는 프레임들이 서로 "정렬"돼 있지 않았던 것이다(별도로
  // 그려진/생성된 이미지라 고양이 몸통 크기·위치가 프레임마다 미묘하게 달랐음). 그래서 다시
  // 찍으면 안 되고, 반드시 "같은 원본 이미지"에서 입 부분만 국소적으로 편집한 프레임끼리만
  // 교차해야 흔들림 없이 입만 움직인다.
  // 지금 쓰는 두 파일(mascot-interview-face.png = 입 다문 정지 이미지, mascot-interview
  // -talking.png = 같은 원본에서 입 부분만 편집한 프레임들을 담은 APNG 애니메이션)은
  // 둘 다 mascot-interview.png 원본을 동일한 크롭 박스로 잘라낸 뒤 입 부분만 그려 넣은
  // 거라 몸통/귀/눈 픽셀 위치가 완전히 동일하다 - 그래서 교차해도 고양이가 흔들리지 않고
  // 입만 움직인다. isSpeaking이 켜지는 순간 APNG가 처음부터 재생되며 자체적으로 루프/타이밍을
  // 관리하므로, JS 쪽에서는 더 이상 프레임을 손으로 토글할 필요가 없다.
  const [isSpeaking, setIsSpeaking] = useState(false);

  // 30초~1분 정도가 일반적인 면접 답변 권장 길이라는 참고 자료 기준 - 절대 기준은
  // 아니고, 감 잡는 용도로만 색을 살짝 바꿔 보여준다.
  const RECOMMENDED_MIN_SEC = 30;
  const RECOMMENDED_MAX_SEC = 60;
  // 2026-08-11: 1.5초 고정 대기 대신 "3, 2, 1" 카운트다운으로 바꿨다(사용자 요청 - 질문을
  // 다 읽어준 다음 "곧 녹화가 시작됩니다, 정면을 응시해주세요"와 숫자 카운트다운을 보여주고
  // 카운트가 끝나는 순간 바로 답변할 수 있게 녹화가 시작되길 원함). "countdown"/"break"
  // 단계와 같은 초당 useEffect 패턴을 재사용한다.
  const GET_READY_COUNTDOWN_SECONDS = 3;
  const FIRST_COUNTDOWN_SECONDS = 3;
  const BREAK_COUNTDOWN_SECONDS = 7; // 2026-08-11: 10초 -> 7초로 단축(사용자 요청)

  // 2026-08-06: 마운트 시 서버에서 클라우드 TTS 음성 목록을 받아온다 - GOOGLE_TTS_API_KEY가
  // 없는 환경(로컬 개발 등)이면 /tts/voices도 실패할 수 있는데, 그 경우 그냥 빈 배열로 두고
  // 음성 선택 UI 자체를 숨긴다(브라우저 기본 TTS만 쓰는 예전 동작으로 자연스럽게 폴백).
  useEffect(() => {
    fetchTtsVoices()
      .then((res) => {
        setTtsVoiceOptions(res.voices);
        setSelectedTtsVoice((prev) => prev || res.default);
      })
      .catch(() => setTtsVoiceOptions([]));
  }, []);

  useEffect(() => {
    getCareerProfile()
      .then((profile) => {
        setCareerJob(profile?.targetRole?.trim() || "");
        setCareerTechSummary(profile?.technicalSummary?.trim() || "");
      })
      .catch(() => {
        setCareerJob("");
        setCareerTechSummary("");
      });
  }, []);

  useEffect(() => {
    if (selectedTtsVoice) localStorage.setItem("mockInterviewTtsVoice", selectedTtsVoice);
  }, [selectedTtsVoice]);

  // 2026-08-13: "첫 질문만 읽어주고 그다음부터 안 읽어준다" 버그 수정.
  // 기존엔 speakQuestionText가 매 질문마다 `new Audio(url)`로 완전히 새 엘리먼트를
  // 만들었는데, iOS 사파리 등 일부 모바일 브라우저의 오토플레이 잠금 해제는 "엘리먼트
  // 단위"로 걸린다 - 첫 질문(시작 버튼 클릭 제스처 직후)은 그 클릭으로 잠금 해제된 새
  // 엘리먼트라 재생되지만, 두 번째 질문부터는 또 다른 새 엘리먼트라 다시 잠겨있는
  // 상태(제스처 밖에서 만들어짐)라 소리 없이 play()가 막힌다. 워치독 타이머가 있어서
  // 화면 진행 자체는 안 멈추니 "다음 질문 텍스트는 뜨는데 소리만 안 남"으로 보인 것.
  // 해결: 오디오 엘리먼트를 질문마다 새로 만들지 않고 하나를 계속 재사용한다(.src만
  // 교체) - 처음 한 번(첫 사용자 제스처)에 이 엘리먼트로 무음 재생을 해두면, 같은
  // 엘리먼트는 이후 제스처 밖에서 src를 바꿔 play()해도 대부분의 모바일 브라우저에서
  // 계속 허용된다(엘리먼트가 "이미 재생 이력이 있다"고 취급됨).
  const getTtsAudioElement = () => {
    if (!ttsAudioRef.current) ttsAudioRef.current = new Audio();
    return ttsAudioRef.current;
  };

  const stopTtsAudio = () => {
    setIsSpeaking(false);
    if (ttsAudioRef.current) {
      ttsAudioRef.current.pause();
      ttsAudioRef.current.onended = null;
      ttsAudioRef.current.onerror = null;
      // 엘리먼트 자체는 재사용을 위해 유지하고, src만 비워 이전 재생 상태를 정리한다.
    }
    if (ttsAudioUrlRef.current) {
      URL.revokeObjectURL(ttsAudioUrlRef.current);
      ttsAudioUrlRef.current = null;
    }
  };

  // 2026-08-12: 휴대폰 브라우저 단독 사용 시 "질문이 안 넘어감" 버그 수정.
  // revealQuestionAndBeginRecording -> speakQuestionText의 audio.play()는 버튼 클릭 이후
  // 카운트다운(setTimeout 체인)을 몇 초 거쳐서야 호출되는데, 이 시점은 더 이상 "사용자 제스처"
  // 콜스택 안이 아니다. 데스크톱 크롬은 대체로 관대하지만, 모바일 사파리/일부 모바일
  // 크롬은 제스처 밖에서 나온 audio.play()를 무음 처리하거나 아예 막는다 - 이 경우
  // audio.onended/onerror가 둘 다 안 불려서 onDone()이 영영 안 불리고(녹음 시작 콜백이
  // startRecording), 화면이 "질문 읽어주는 중" 상태에서 멈춰버린다(캠/마이크는 이미
  // 앞단계에서 정상 연결됐으니 "캠은 되는데 질문만 안 넘어간다"처럼 보임).
  // 해결: 사용자가 처음 누르는 버튼(마이크/캠 테스트 시작, 면접 시작) 클릭 핸들러 "안"에서
  // 무음 오디오를 한 번 실제로 재생해둔다 - 대부분의 모바일 브라우저는 제스처 중 오디오
  // 재생이 한 번 성공하면 그 페이지 전체에 대해 이후의 스크립트 재생도 풀어준다(오토플레이
  // 잠금 해제). 실패해도(=이미 막혀있는 브라우저) 그냥 무시 - 아래 워치독 타이머가 마지막
  // 안전망 역할을 한다.
  const audioUnlockedRef = useRef(false);
  const unlockAudioPlaybackForMobile = () => {
    if (audioUnlockedRef.current) return;
    audioUnlockedRef.current = true;
    try {
      // 2026-08-13: speakQuestionText가 매 질문마다 재사용할 "바로 그 엘리먼트"로 무음
      // 재생을 해둔다(별도의 throwaway Audio가 아니라 getTtsAudioElement()로 가져온 동일
      // 엘리먼트) - iOS 사파리는 엘리먼트별로 잠금 해제 여부를 기억하기 때문에, 나중에
      // 실제 질문 오디오를 재생할 엘리먼트와 지금 잠금 해제하는 엘리먼트가 같아야 효과가 있다.
      const silentWavDataUrl =
        "data:audio/wav;base64,UklGRiYAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQIAAAAAAA==";
      const unlockAudio = getTtsAudioElement();
      unlockAudio.src = silentWavDataUrl;
      unlockAudio.volume = 0;
      void unlockAudio.play().catch(() => {
        // 여기서 막혀도(엄격한 브라우저) 아래 워치독이 있으니 흐름 자체는 이어진다.
      });
    } catch {
      // no-op - 최선의 시도일 뿐이다.
    }
  };

  // 브라우저 기본 TTS로 재생 - 클라우드 TTS 키가 없거나 요청이 실패했을 때의 폴백(fail-open).
  // onDone은 다 읽고 나면(또는 speechSynthesis 자체를 못 쓰면 1.8초 뒤) 정확히 한 번 불린다.
  const speakWithBrowserTts = (text: string, onDone: () => void) => {
    const hasSpeechSynthesis = typeof window.speechSynthesis !== "undefined";
    if (!hasSpeechSynthesis) {
      window.setTimeout(onDone, 1800);
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    utterance.onend = onDone;
    utterance.onerror = onDone;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  };

  // 2026-08-06: 질문을 읽어준다 - 먼저 선택된 클라우드 TTS 음성으로 시도하고(더 자연스러운
  // 목소리), 키가 없거나 요청/재생이 실패하면 브라우저 기본 TTS로 자동 전환한다(사전적인
  // 기계음성이지만 최소한 안 끊긴다). onDone은 어느 경로로 끝나든 정확히 한 번만 불린다 -
  // revealQuestionAndBeginRecording이 이걸로 녹음 시작 타이밍을 잡는다.
  const speakQuestionText = (text: string, onDone: () => void) => {
    stopTtsAudio();
    window.speechSynthesis?.cancel();
    if (!text) {
      onDone();
      return;
    }
    setIsSpeaking(true);

    // 2026-08-12: 워치독 - onended/onerror가 둘 다 안 불리고 조용히 멈추는 경우(모바일에서
    // 재생이 막혔는데 catch도 안 걸리는 등)에 대비한 최종 안전망. 이게 없으면 "질문 읽어주는
    // 중" 상태에서 영원히 멈춰서 다음 단계(녹음 시작)로 못 넘어간다. 글자 수로 대략적인
    // 낭독 시간을 추정해(한국어 분당 약 300자) 최소 6초, 최대 20초로 여유를 둔다 - 정상
    // 경로가 먼저 끝나면 이 타이머는 취소된다.
    let settled = false;
    let watchdogId: number | null = null;
    const finish = () => {
      if (settled) return;
      settled = true;
      if (watchdogId !== null) {
        window.clearTimeout(watchdogId);
        watchdogId = null;
      }
      stopTtsAudio();
      window.speechSynthesis?.cancel();
      onDone();
    };
    const estimatedMs = Math.min(20000, Math.max(6000, (text.length / 300) * 60000 + 2000));
    watchdogId = window.setTimeout(finish, estimatedMs);

    synthesizeSpeech(text, selectedTtsVoice)
      .then((blob) => {
        if (settled) return; // 워치독이 이미 다음 단계로 넘겼으면 새로 재생을 시작할 필요 없음
        const url = URL.createObjectURL(blob);
        ttsAudioUrlRef.current = url;
        // 질문마다 새 Audio()를 만들지 않고 재사용 엘리먼트의 src만 바꾼다 - 모바일
        // 오토플레이 잠금 해제가 엘리먼트 단위라 이래야 두 번째 질문부터도 소리가 난다.
        const audio = getTtsAudioElement();
        audio.volume = 1;
        audio.src = url;
        audio.onended = finish;
        audio.onerror = () => speakWithBrowserTts(text, finish);
        void audio.play().catch(() => speakWithBrowserTts(text, finish));
      })
      .catch(() => {
        if (!settled) speakWithBrowserTts(text, finish);
      });
  };

  // 수동으로 "질문 듣기"를 다시 누를 때 쓰는 재생 함수 - 인자 없이 부르면 현재 question을 읽는다.
  // 이땐 다 읽은 뒤 이어서 할 일이 없어서 onDone은 아무것도 안 한다.
  const speakQuestion = (text: string = question) => {
    if (!text) return;
    speakQuestionText(text, () => {});
  };

  // 2026-08-05: 세션 시작 시 질문을 한 번에 준비한다 - 실제 면접 관례대로 1번째 질문은
  // 항상 자기소개로 고정하고(모델이 스스로 자기소개 질문을 규칙적으로 만들어내지는 않아서
  // 강제로 넣음), 나머지는 ai-server 생성 모델을 병렬로 호출해서 받는다(순차로 하면 질문
  // 하나당 6~8초씩 걸려서 대기시간이 개수만큼 늘어남). 모델 호출이 실패하면
  // SAMPLE_QUESTIONS로 폴백한다.
  // 2026-08-06: 원래 "질문 3개(자기소개+고정 카테고리 2개)"로 하드코딩돼 있었는데, "질문
  // 개수도 카드로 고르게" 요청으로 questionCount(3/5/7)만큼 나머지 질문을 카테고리를
  // 순환시키며 생성하도록 일반화했다. 카테고리를 안 넘기면 매번 무작위에 가까운 카테고리가
  // 나와서, 서로 다른 카테고리를 순서대로 배정해 질문이 겹치는 느낌을 줄인다. 체험판/결제
  // 등급별로 어떤 카테고리를 줄지는 결제(크레딧) 기능 설계(태스크 #1)에서 정해지는 대로 이
  // 배열을 등급에 맞게 바꿔 끼우면 된다 - 지금은 고정값.
  const NON_INTRO_CATEGORIES = [
    "기술_직무역량", "문제해결_도전경험", "협업_리더십_커뮤니케이션", "가치관_자기관리", "강점_약점",
  ] as const;

  // 2026-08-07: tech_summary가 "VSCode 확장 프로그램 개발 경험"처럼 짧고 구체적인 한 줄일
  // 때, 같은 job/tech_summary로 세션 안에서 여러 번(질문 개수만큼) 호출하면 매번 같은
  // 소재로 질문이 수렴할 수 있다는 우려로 추가 - 질문마다 명시적으로 다른 관점을 지정해서
  // 보낸다(question_generator.py generate_personalized_question의 angle_hint 설계 메모
  // 참고). 특히 "직무면접" 유형만 골랐을 때(카테고리 풀이 기술_직무역량 하나뿐이라 매
  // 질문이 다 이 카테고리를 씀) 효과가 크다.
  const TECH_QUESTION_ANGLES = [
    "기술 선택 이유", "트러블슈팅/문제 해결 경험", "설계·트레이드오프 판단",
    "성능·품질 개선 경험", "협업 중 기술적 의견 차이 조율", "실무 적용 사례·한계",
  ] as const;

  const buildSessionQuestions = async (): Promise<string[]> => {
    // 2026-08-13: "프로필 불러오기"를 명시적으로 고르고 구독 중(또는 관리자)일 때만
    // careerJob/careerTechSummary를 실제로 흘려보낸다 - "연습"이거나 비구독자면 프로필이
    // 있어도 아예 안 쓴다(이 화면의 useSubscriptionGatedProfile 참고). 시작 화면에서
    // 분야를 직접 골랐다면(selectedRole) 그 라벨이 항상 우선한다 - 이건 "프로필을 불러온
    // 것"이 아니라 그 자리에서 명시적으로 고른 값이라 연습 모드에서도 그대로 반영된다.
    const useProfile = questionSource === "profile" && subscribed;
    const selectedRoleLabel = INTERVIEW_ROLE_OPTIONS.find(([code]) => code === selectedRole)?.[1];
    const effectiveJob = selectedRoleLabel || (useProfile ? careerJob : "") || undefined;
    const effectiveTechSummary = useProfile ? careerTechSummary : "";

    // 2026-08-07: 면접 유형(역량/직무/인성)을 골랐으면 그 유형에 속한 카테고리만 순환시킨다 -
    // 안 고르면("전체") 기존처럼 6개 카테고리 다 순환하는 동작 그대로 유지(하위 호환).
    const selectedTypeCategories = INTERVIEW_TYPE_OPTIONS.find(
      ([type]) => type === selectedInterviewType,
    )?.[1];
    const categoryPool = selectedTypeCategories ?? NON_INTRO_CATEGORIES;

    const categoriesNeeded = Math.max(0, questionCount - 1);
    const categories = Array.from(
      { length: categoriesNeeded },
      (_, i) => categoryPool[i % categoryPool.length],
    );
    // 2026-08-10 버그 수정: TECH_QUESTION_ANGLES("기술 선택 이유", "트러블슈팅 경험" 등)를
    // 카테고리 상관없이 모든 질문에 무조건 붙이고 있었다 - angle_hint는 Gemini 프롬프트에서
    // "반드시 이 관점에서 만들어라"는 강제 규칙이라, "가치관_자기관리"(인성면접) 같은
    // 비기술 카테고리에도 기술 관점이 덮어써져서 인성면접을 골라도 기술 질문이 나오는
    // 버그가 있었다("인성면접 눌렀는데 왜 기술 스택 질문이 나오냐" 리포트로 발견). 원래
    // 의도(TECH_QUESTION_ANGLES 선언부 주석 참고)대로 "기술_직무역량" 카테고리일 때만
    // 붙이고, 그 외 카테고리는 angle_hint 없이 보내서 Gemini가 기존 다양성 규칙(5번,
    // question_generator.py generate_personalized_question 참고)을 대신 쓰게 한다.
    const results = await Promise.allSettled(
      categories.map((category, i) =>
        fetchNextQuestion(
          effectiveJob,
          undefined,
          category,
          effectiveTechSummary || undefined,
          category === "기술_직무역량" ? TECH_QUESTION_ANGLES[i % TECH_QUESTION_ANGLES.length] : undefined,
        ),
      ),
    );

    const questions: string[] = [SELF_INTRO_QUESTION];
    for (const r of results) {
      const candidate = r.status === "fulfilled" ? r.value.question : null;
      if (candidate && !questions.includes(candidate)) {
        questions.push(candidate);
      } else {
        questions.push(pickFallbackQuestion(...questions));
      }
    }
    return questions;
  };

  // 2026-08-06: 원래 "질문 생성 중..." 화면에서 완전히 다 끝날 때까지 막아놓고 그다음에야
  // 마이크·카메라 테스트로 넘어갔는데, 로컬 LoRA 모델 추론(질문 2개, 순차면 최대 16초)이
  // 체감상 너무 느리다는 피드백을 받았다. 질문 생성과 마이크/캠 테스트는 서로 의존관계가
  // 없으므로(질문은 "모의면접 시작하기"를 눌러 카운트다운이 시작될 때만 실제로 필요함) 굳이
  // 순서대로 기다릴 이유가 없다 - 시작하자마자 질문 생성은 백그라운드로 흘려보내고, 화면은
  // 곧바로 device-check(마이크·카메라 테스트 안내)로 넘긴다. 사용자가 권한 허용하고 얼굴
  // 인식 모델 로딩하고 마이크 레벨 확인하는 그 몇 초 동안 질문 생성이 같이 진행되니, 실제
  // 답변을 시작하려는 시점("모의면접 시작하기" 버튼)엔 이미 다 준비돼 있을 확률이 높다 -
  // 그 버튼만 sessionQuestions가 채워질 때까지 비활성화해서 안전하게 막아둔다.
  const startSession = () => {
    setErrorMessage(null);
    setSessionQuestions([]);
    setSessionIndex(0);
    setChatOnlyMode(false);
    void buildSessionQuestions().then((questions) => {
      setSessionQuestions(questions);
    });
    setStage("device-check");
  };

  const stopMeterLoop = () => {
    if (rafIdRef.current !== null) cancelAnimationFrame(rafIdRef.current);
    rafIdRef.current = null;
    void audioContextRef.current?.close();
    audioContextRef.current = null;
  };

  const startMeterLoop = (stream: MediaStream) => {
    stopMeterLoop();
    if (stream.getAudioTracks().length === 0) {
      setMicLevel(0);
      return;
    }
    const audioContext = new AudioContext();
    audioContextRef.current = audioContext;
    const source = audioContext.createMediaStreamSource(stream);
    const analyser = audioContext.createAnalyser();
    analyser.fftSize = 1024;
    source.connect(analyser);

    const data = new Uint8Array(analyser.fftSize);
    const tick = () => {
      analyser.getByteTimeDomainData(data);
      let peak = 0;
      for (const value of data) peak = Math.max(peak, Math.abs((value - 128) / 128));
      setMicLevel(Math.min(100, Math.round(peak * 250)));
      drawWaveform(data);
      rafIdRef.current = requestAnimationFrame(tick);
    };
    tick();
  };

  // 2026-08-12 추가: analyser의 시간 영역 데이터(byte time-domain data)를 그대로 캔버스에
  // 선으로 그려서 실제 파형(물결 모양)을 보여준다 - micLevel(막대 하나)보다 목소리의
  // 진짜 모양(떨림, 강세)이 눈에 보여서 "마이크가 소리를 잘 잡고 있다"는 확신을 주기 좋다.
  const drawWaveform = (data: Uint8Array) => {
    const canvas = waveformCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // 레티나 디스플레이에서 흐릿하게 안 보이도록 devicePixelRatio만큼 실제 픽셀을 늘리고
    // CSS 크기는 그대로 유지한다 - canvas 표준 패턴.
    const dpr = window.devicePixelRatio || 1;
    const cssWidth = canvas.clientWidth || 320;
    const cssHeight = canvas.clientHeight || 56;
    if (canvas.width !== cssWidth * dpr || canvas.height !== cssHeight * dpr) {
      canvas.width = cssWidth * dpr;
      canvas.height = cssHeight * dpr;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssWidth, cssHeight);

    ctx.lineWidth = 2;
    ctx.strokeStyle = "#596ff3";
    ctx.beginPath();
    const step = cssWidth / data.length;
    for (let i = 0; i < data.length; i++) {
      const x = i * step;
      const y = (data[i] / 255) * cssHeight; // byte time-domain: 128이 무음(중앙)
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  };

  const disconnectPhonePairing = () => {
    phonePairDisconnectRef.current?.();
    phonePairDisconnectRef.current = null;
    phonePairStateRef.current = null;
    phoneAutoStartRef.current = false;
  };

  const stopFaceLoop = () => {
    if (faceRafIdRef.current !== null) cancelAnimationFrame(faceRafIdRef.current);
    faceRafIdRef.current = null;
  };

  const stopStream = () => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    if (videoRef.current) videoRef.current.srcObject = null;
  };

  const usePhoneCameraStream = async (stream: MediaStream) => {
    stopStream();
    streamRef.current = stream;
    if (videoRef.current) {
      videoRef.current.srcObject = stream;
      await videoRef.current.play();
    }
    const landmarker = landmarkerRef.current ?? await loadFaceLandmarker();
    landmarkerRef.current = landmarker;
    startFaceTrackingLoop(landmarker);
    startMeterLoop(stream);
    setCameraReady(true);
    setPairingPanelOpen(false);
    // QR pairing hands the whole interview to the phone camera. It should not
    // leave the user at a second manual "start" button on the PC.
    phoneAutoStartRef.current = true;
    setStage("testing-mic");
  };

  useEffect(() => {
    if (!phoneAutoStartRef.current || stage !== "testing-mic" || sessionQuestions.length === 0) return;
    phoneAutoStartRef.current = false;
    beginInterviewCountdown();
    // beginInterviewCountdown reads the latest prepared session questions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, sessionQuestions]);

  useEffect(() => {
    phonePairStateRef.current?.(stage, questionTextReady ? question : undefined, stage === "recording" ? elapsedSec : undefined);
  }, [stage, question, questionTextReady, elapsedSec]);

  // 눈에 보이는 피드백이 있어야 "지금 분석되고 있다"는 게 체감된다 - 캔버스에 얼굴
  // 랜드마크 점을 실시간으로 그려준다. 녹음 시작 전(테스트 단계)부터 계속 돌리다가,
  // 실제 녹음 중(isRecordingRef)일 때만 지표 계산용 샘플을 같이 모은다.
  const startFaceTrackingLoop = (landmarker: Awaited<ReturnType<typeof loadFaceLandmarker>>) => {
    const videoEl = videoRef.current;
    if (!videoEl) return;

    const loop = () => {
      try {
        if (videoEl.videoWidth > 0 && canvasRef.current) {
          const canvas = canvasRef.current;
          if (canvas.width !== videoEl.videoWidth || canvas.height !== videoEl.videoHeight) {
            canvas.width = videoEl.videoWidth;
            canvas.height = videoEl.videoHeight;
          }
          const ctx = canvas.getContext("2d");
          const now = performance.now();
          const detection = landmarker.detectForVideo(videoEl, now);
          if (ctx) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            // 고정 가이드 타원 - "여기에 얼굴을 맞추세요" 안내선. 인식 여부와 무관하게 항상 그림.
            // 이건 가이드니까 선명하게 - 얼굴을 가리는 건 아래쪽 윤곽선 쪽만 옅게 하면 됨.
            ctx.strokeStyle = "rgba(255,214,0,0.85)";
            ctx.lineWidth = 2;
            ctx.setLineDash([6, 6]);
            ctx.beginPath();
            ctx.ellipse(canvas.width / 2, canvas.height / 2, canvas.width * 0.26, canvas.height * 0.42, 0, 0, Math.PI * 2);
            ctx.stroke();
            ctx.setLineDash([]);

            // 실제 인식된 얼굴 윤곽선 - 점 468개를 다 찍지 않고 턱선/이마 라인만 이어서 그림.
            // 이것도 옅게 그려서 밑에 얼굴이 그대로 잘 보이게 한다.
            const landmarks = detection.faceLandmarks?.[0];
            if (landmarks) {
              ctx.strokeStyle = "rgba(92,225,230,0.5)";
              ctx.lineWidth = 1.5;
              ctx.beginPath();
              FACE_OVAL_INDICES.forEach((index, i) => {
                const point = landmarks[index];
                if (!point) return;
                const x = point.x * canvas.width;
                const y = point.y * canvas.height;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
              });
              ctx.stroke();

              // 요청: 랜드마크 점도 같이 보이게 하되, 예전처럼 촘촘해서 얼굴을 가리지
              // 않도록 흰색 반투명 + 아주 작은 크기로 옅게 찍는다.
              ctx.fillStyle = "rgba(255,255,255,0.45)";
              for (const point of landmarks) {
                const x = point.x * canvas.width;
                const y = point.y * canvas.height;
                ctx.beginPath();
                ctx.arc(x, y, 1.2, 0, Math.PI * 2);
                ctx.fill();
              }
            }
          }
          if (isRecordingRef.current) {
            const sample = sampleFrame(detection, now);
            if (sample) faceFramesRef.current.push(sample);
          }
        }
      } catch {
        // 프레임 하나 실패해도 계속 진행한다.
      }
      faceRafIdRef.current = requestAnimationFrame(loop);
    };
    loop();
  };

  // 마이크 음량 확인 + 얼굴 인식 준비를 한 번에 한다 - 카메라/마이크 권한 요청을
  // 따로따로 하면 사용자가 두 번 허용해야 해서 번거롭다.
  const startDeviceTest = async () => {
    unlockAudioPlaybackForMobile();
    setErrorMessage(null);
    setStage("preparing");
    try {
      disconnectPhonePairing();
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { width: 640, height: 480 } });
      streamRef.current = stream;

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }

      // 얼굴 인식 모델(WASM+모델 파일)을 CDN에서 받아오는 데 몇 초 걸릴 수 있어서
      // "준비 중" 단계로 따로 보여준다.
      const landmarker = await loadFaceLandmarker();
      landmarkerRef.current = landmarker;
      setCameraReady(true);
      startFaceTrackingLoop(landmarker);

      startMeterLoop(stream);

      setStage("testing-mic");
    } catch (error) {
      // NotAllowedError: 권한을 거부한 경우. NotFoundError: 이 기기에 마이크/카메라
      // 자체가 없는 경우(예: 캠 없는 데스크톱) - 이때는 휴대폰으로 접속하라고 안내한다.
      setErrorMessage(
        error instanceof DOMException && error.name === "NotAllowedError"
          ? "마이크/카메라 권한이 거부되었습니다. 브라우저 주소창의 자물쇠 아이콘에서 권한을 허용해 주세요."
          : error instanceof DOMException && error.name === "NotFoundError"
            ? "이 기기에서 마이크나 카메라를 찾을 수 없습니다. 컴퓨터에 캠/마이크가 없다면, 휴대폰으로 이 페이지에 접속해서 진행해 주세요."
            : "마이크나 카메라에 접근할 수 없습니다. 이 기기에 연결되어 있는지 확인해 주세요.",
      );
      setStage("error");
    }
  };

  const cancelDeviceTest = () => {
    stopMeterLoop();
    stopFaceLoop();
    isRecordingRef.current = false;
    disconnectPhonePairing();
    stopStream();
    setCameraReady(false);
    setStage("device-check");
  };

  // 2026-08-05: 마이크/캠 없이 텍스트로 진행하는 보조 경로 - 세션의 첫 질문을 바로 공개하고
  // 타이핑 화면으로 보낸다(이 경로는 "실전처럼 숨겼다 공개"하는 연출까지는 굳이 필요 없다고
  // 판단 - 이미 주 기능이 아니라고 명시한 보조 경로라 단순하게 유지).
  const startTypingForSession = () => {
    const first = sessionQuestions[0] ?? SAMPLE_QUESTIONS[0];
    currentQuestionRef.current = first;
    setQuestion(first);
    setAnswerMode("text");
    setStage("typing");
  };

  // 2026-08-06: 시작화면의 "채팅으로 연습하기" 카드 전용 진입점 - 팝업 위젯을 열던 걸
  // 없애고, 카메라/마이크 모드처럼 화면 전체가 바뀌는 흐름으로 곧장 들어간다.
  // buildSessionQuestions가 끝나기 전엔 question이 빈 문자열이라 "typing" 화면이 로딩
  // 상태를 보여준다(아래 JSX questionLoading 참고) - device-check 화면 없이 바로 여기로
  // 오기 때문에 startSession처럼 백그라운드로 흘려보내지 않고 결과를 기다렸다가 채운다.
  const startChatModeSession = () => {
    setErrorMessage(null);
    setSessionQuestions([]);
    setSessionIndex(0);
    setAnswerMode("text");
    setChatOnlyMode(true);
    setQuestion("");
    currentQuestionRef.current = "";
    setStage("typing");
    void buildSessionQuestions().then((questions) => {
      setSessionQuestions(questions);
      const first = questions[0] ?? SAMPLE_QUESTIONS[0];
      currentQuestionRef.current = first;
      setQuestion(first);
    });
  };

  // 버튼 누르자마자 녹음을 시작하면 말할 준비가 안 된 채로 앞부분이 침묵으로 날아가는
  // 경우가 많아서(테스트 중 실제로 겪음), 짧게 준비 시간을 준 다음 녹음을 시작한다.
  const startRecording = () => {
    stopMeterLoop();
    if (!streamRef.current) {
      setErrorMessage("마이크/카메라 스트림을 찾을 수 없습니다. 다시 시도해 주세요.");
      setStage("error");
      return;
    }
    setCountdownTotal(GET_READY_COUNTDOWN_SECONDS);
    setCountdownValue(GET_READY_COUNTDOWN_SECONDS);
    setStage("get-ready");
  };

  const beginActualRecording = () => {
    const stream = streamRef.current;
    if (!stream) {
      setErrorMessage("마이크/카메라 스트림을 찾을 수 없습니다. 다시 시도해 주세요.");
      setStage("error");
      return;
    }

    // 2026-08-13: "아이폰 사파리에서 녹음은 되는데(에러도 안 뜸) 전사가 계속 빈칸으로 나온다"
    // 버그 리포트 원인 - webm/ogg 컨테이너를 MediaRecorder가 아예 지원 안 하는 브라우저는
    // 사실상 WebKit(iOS/모든 iOS 브라우저 + macOS Safari) 계열뿐이다. 그런데 아래처럼
    // "오디오 트랙만 뽑아서 새로 만든 MediaStream"을 WebKit의 MediaRecorder에 넘기면, 예외는
    // 안 던지고 녹음도 "성공"하지만 실제로는 무음이 녹음되는 WebKit 버그가 있다(원본 stream의
    // 트랙을 그대로 안 쓰고 getAudioTracks()로 뽑아 새 MediaStream을 만드는 지점이 문제) - 그
    // 결과 서버(ffmpeg->Google STT)는 정상적으로 "무음"을 처리해서 빈 전사를 돌려준 것뿐이라
    // 에러도 안 뜬 것이다. 크롬 계열은 webm/ogg 중 하나가 항상 지원되니 기존처럼 오디오 트랙만
    // 추출해서 녹음(대역폭 절약)하고, WebKit 계열로 판별되면 트랙 추출 없이 원본 stream(영상+
    // 오디오)을 그대로 녹음한다 - ai-server는 어차피 ffmpeg로 오디오만 뽑아 쓰므로 영상이
    // 섞여 있어도 STT 동작은 동일하고, 대신 업로드 용량이 좀 더 커진다.
    const audioMimeType = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus"]
      .find((candidate) => MediaRecorder.isTypeSupported(candidate));
    const isLikelyWebkit = !audioMimeType;

    let recordStream = stream;
    if (!isLikelyWebkit) {
      // STT 서버에는 오디오만 보내면 되니, 녹음 자체는 오디오 트랙만 따로 담아서 만든다
      // (영상까지 녹화해서 올리면 용량도 크고 서버에 얼굴 영상을 보내는 셈이 되어버림) -
      // 다만 이건 위에서 설명한 WebKit 무음 버그를 피할 수 있는 브라우저(크롬 계열)에서만.
      const audioOnlyStream = new MediaStream(stream.getAudioTracks());
      if (audioOnlyStream.getAudioTracks().length === 0) {
        setErrorMessage("마이크 오디오를 받지 못했습니다. 브라우저에서 마이크 권한을 허용한 뒤 다시 연결해 주세요.");
        setStage("error");
        return;
      }
      recordStream = audioOnlyStream;
    }

    chunksRef.current = [];
    // WebKit 계열은 webm/ogg 대신 mp4(audio/mp4, video/mp4)만 지원한다.
    const mimeType = isLikelyWebkit
      ? ["video/mp4", "audio/mp4"].find((candidate) => MediaRecorder.isTypeSupported(candidate))
      : audioMimeType;
    let recorder: MediaRecorder;
    try {
      recorder = mimeType ? new MediaRecorder(recordStream, { mimeType }) : new MediaRecorder(recordStream);
      recorder.start();
    } catch (reason) {
      // Some Chromium builds reject an audio-only MediaStream made from a
      // remote WebRTC track. The AI server accepts WebM and extracts audio
      // with ffmpeg, so use the original remote audio+video stream as a safe
      // fallback instead of abandoning the interview.
      const fallbackMimeType = ["video/webm;codecs=vp8,opus", "video/webm", "video/mp4"]
        .find((candidate) => MediaRecorder.isTypeSupported(candidate));
      try {
        recorder = fallbackMimeType
          ? new MediaRecorder(stream, { mimeType: fallbackMimeType })
          : new MediaRecorder(stream);
        recorder.start();
      } catch (fallbackReason) {
        setErrorMessage(`녹화를 시작하지 못했습니다. ${fallbackReason instanceof Error ? fallbackReason.message : reason instanceof Error ? reason.message : "휴대폰의 마이크 권한을 다시 확인해 주세요."}`);
        setStage("error");
        return;
      }
    }
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunksRef.current.push(event.data);
    };
    recorder.onstop = () => void submitRecording();

    mediaRecorderRef.current = recorder;

    // 얼굴 추적 자체는 이미 테스트 단계부터 돌고 있었고, 여기서는 지표 계산용
    // 샘플을 이제부터 모으라고 표시만 해준다 (faceFramesRef를 녹음 시작 시점에 비움).
    faceFramesRef.current = [];
    isRecordingRef.current = true;

    // 2026-08-05: 방어 코드 - 어떤 이유로든(더블클릭 등) startRecording이 두 번 불리면
    // 기존 타이머를 안 지우고 새로 만들어서 초가 2씩(또는 그 이상) 뛰는 문제가 있었다.
    // 새로 시작하기 전에 기존 타이머가 있으면 반드시 먼저 지운다.
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }
    setElapsedSec(0);
    timerIdRef.current = window.setInterval(() => {
      setElapsedSec((prev) => prev + 1);
    }, 1000);

    setStage("recording");
  };

  // 2026-08-05: 마이크·카메라 테스트를 마치고 "면접 시작"을 누르면 곧바로 질문을 보여주지
  // 않고 카운트다운부터 돌린다 - 아래 useEffect가 매초 값을 줄이다가 0이 되는 순간
  // revealQuestionAndBeginRecording을 호출해 질문을 공개한다.
  const beginInterviewCountdown = () => {
    unlockAudioPlaybackForMobile();
    stopMeterLoop();
    setQuestionTextReady(false);
    pendingQuestionRef.current = sessionQuestions[sessionIndex] ?? null;
    setCountdownTotal(FIRST_COUNTDOWN_SECONDS);
    setCountdownValue(FIRST_COUNTDOWN_SECONDS);
    setStage("countdown");
  };

  useEffect(() => {
    if (stage !== "countdown") return;
    if (countdownValue <= 0) {
      const next = pendingQuestionRef.current;
      if (next) revealQuestionAndBeginRecording(next);
      return;
    }
    const timer = window.setTimeout(() => setCountdownValue((v) => v - 1), 1000);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, countdownValue]);

  // 2026-08-11: 질문을 다 읽어준 뒤 "곧 녹화가 시작됩니다" 3-2-1 카운트다운 - 위
  // "countdown"(질문 공개 전) 단계와 같은 패턴이지만, 끝났을 때 부르는 함수만 다르다
  // (revealQuestionAndBeginRecording 대신 beginActualRecording).
  useEffect(() => {
    if (stage !== "get-ready") return;
    if (countdownValue <= 0) {
      beginActualRecording();
      return;
    }
    const timer = window.setTimeout(() => setCountdownValue((v) => v - 1), 1000);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, countdownValue]);

  // 2026-08-06: 답변 사이 10초 휴식 - 이 동안은 얼굴 추적 루프 자체가 꺼져 있어서(카메라
  // 미리보기도 안 보여줌) 분석 대상 시간에 절대 섞이지 않는다. 이 10초는 방금 답변의 분석
  // (analyzeAnswer API 호출)과 병렬로 흐른다 - stopRecording에서 분석은 백그라운드로 바로
  // 시작해두고 화면엔 이 쉬는 시간 모션만 보여준다("분석 시간 + 10초"로 순차로 길어지던 걸
  // 겹치게 해서 줄였다). 그래서 카운트다운이 다 끝나도 곧장 다음 단계로 넘기지 않고,
  // 분석 결과(pendingAnalysisRef)가 이미 준비돼 있을 때만 이어서 처리한다 - 분석이 더 늦게
  // 끝나는 드문 경우엔 handleAnalysisReady 쪽에서 이어받는다(아래 참고).
  useEffect(() => {
    if (stage !== "break") return;
    if (countdownValue <= 0) {
      breakCountdownDoneRef.current = true;
      const ready = pendingAnalysisRef.current;
      if (ready) {
        pendingAnalysisRef.current = null;
        finishAnswer(ready.analysis, ready.faceMetrics);
      }
      return;
    }
    const timer = window.setTimeout(() => setCountdownValue((v) => v - 1), 1000);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, countdownValue]);

  // 카운트다운이 끝나는 순간 호출된다 - 질문 텍스트를 화면에 공개하고 동시에 음성으로
  // 2026-08-06: 타이핑 화면에 새 질문이 뜨면(question이 바뀌면) 제한시간을 90초로 리셋한다.
  useEffect(() => {
    if (stage === "typing" && question) setTypingSecondsLeft(TYPING_TIME_LIMIT_SEC);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, question]);

  // 매초 줄어드는 제한시간 타이머 - "break"/"countdown" 단계와 같은 setTimeout 패턴.
  useEffect(() => {
    if (stage !== "typing" || !question || typingSecondsLeft <= 0) return;
    const timer = window.setTimeout(() => setTypingSecondsLeft((v) => v - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [stage, question, typingSecondsLeft]);

  // 시간이 다 됐을 때 - 이미 입력한 내용이 있으면 그대로 자동 제출(실전처럼 시간 압박),
  // 아무것도 안 썼으면 그냥 0에 멈춰서 사용자가 뭐라도 입력하게 둔다(빈 답변 제출 방지).
  useEffect(() => {
    if (stage === "typing" && typingSecondsLeft === 0 && typedAnswer.trim()) {
      submitTypedAnswer();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [typingSecondsLeft]);

  // 읽어준 다음, TTS가 끝나는 시점(onend)에 맞춰 녹음 준비(get-ready)로 넘어간다.
  // TTS가 끝나기도 전에 녹음을 시작하면 스피커로 나오는 질문 음성이 마이크에 다시
  // 잡혀 답변 인식에 섞여 들어갈 수 있어서, 반드시 다 읽고 나서 시작한다.
  const revealQuestionAndBeginRecording = (text: string) => {
    currentQuestionRef.current = text;
    setQuestion(text);
    // 2026-08-06: 텍스트를 세팅하는 이 시점부터 바로 화면에 질문을 보여준다(TTS가 읽어주는
    // 동안에도 같이 보이게) - questionTextReady 선언부 주석 참고.
    setQuestionTextReady(true);
    speakQuestionText(text, () => startRecording());
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
    isRecordingRef.current = false;
    // 2026-08-05: 예전엔 여기서 stopStream()까지 같이 불러서 한 문제(답변)가 끝날 때마다
    // 카메라/마이크가 완전히 꺼졌다 - 세션 안에서 다음 질문으로 넘어갈 때 매번 권한을 다시
    // 요청해야 해서 실전 흐름이 끊겼다. 이제는 얼굴 추적 루프만 멈추고(다음 질문 시작 시
    // startFaceTrackingLoop로 재개) 스트림 자체는 세션이 완전히 끝날 때(endSession)만 끈다.
    stopFaceLoop();
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }

    pendingAnalysisRef.current = null;
    breakCountdownDoneRef.current = false;

    // 2026-08-06: 마지막 질문이면 어차피 다음 질문이 없어서 쉬는 시간이 필요 없으니 그대로
    // "분석 중" 대기만 보여준다. 마지막이 아니면 분석(submitRecording, recorder.onstop으로
    // 곧 비동기 실행됨)은 백그라운드로 흘려보내고, 화면엔 10초 쉬는 시간 모션만 보여준다 -
    // 예전엔 "분석 중"과 "쉬는 시간"이 순차로 이어져서 체감 대기시간이 길었는데, 이제 둘이
    // 동시에 흐른다(늦게 끝나는 쪽 기준으로 다음 질문으로 넘어감 - handleAnalysisReady,
    // break useEffect 참고).
    const isLastQuestion = sessionQuestions.length === 0 || sessionIndex >= sessionQuestions.length - 1;
    if (isLastQuestion) {
      setStage("analyzing");
    } else {
      setCountdownTotal(BREAK_COUNTDOWN_SECONDS);
      setCountdownValue(BREAK_COUNTDOWN_SECONDS);
      setStage("break");
    }
  };

  // 2026-08-06: analyzeAnswer 결과가 도착했을 때 호출된다. 마지막 질문(쉬는 시간 없이 바로
  // "분석 중"만 보여준 경우)이면 곧장 다음 단계(session-report)로 넘긴다. 마지막이 아니면
  // 10초 쉬는 시간 카운트다운과 경합 상태다 - 쉬는 시간이 이미 끝나 있었으면(breakCountdownDoneRef)
  // 바로 이어서 처리하고, 아직 쉬는 중이면 결과만 저장해두고 쉬는 시간 쪽 useEffect가
  // 카운트다운이 0이 되는 순간 이어받는다.
  const handleAnalysisReady = (analysis: AnswerAnalysis, faceMetricsResult: FaceMetrics | null) => {
    const isLastQuestion = sessionQuestions.length === 0 || sessionIndex >= sessionQuestions.length - 1;
    if (isLastQuestion || breakCountdownDoneRef.current) {
      breakCountdownDoneRef.current = false;
      finishAnswer(analysis, faceMetricsResult);
      return;
    }
    pendingAnalysisRef.current = { analysis, faceMetrics: faceMetricsResult };
  };

  const submitRecording = async () => {
    try {
      // 2026-08-13: 예전엔 실제 녹음 포맷과 무관하게 무조건 "audio/webm"/"answer.webm"으로
      // 고정해서 보냈다 - WebKit(iOS Safari 등)에서는 실제로 mp4로 녹음되는데 라벨만 webm인
      // 상태였던 것. ai-server의 ffmpeg가 내용을 보고 알아서 디코딩하긴 하지만, 라벨이
      // 실제 포맷과 다르면 나중에 문제가 생겼을 때 로그로 원인을 추적하기 어려워서 recorder가
      // 실제로 사용한 mimeType을 그대로 반영한다.
      const actualMimeType = mediaRecorderRef.current?.mimeType || "audio/webm";
      const extension = actualMimeType.includes("mp4") ? "mp4" : actualMimeType.includes("ogg") ? "ogg" : "webm";
      const blob = new Blob(chunksRef.current, { type: actualMimeType });
      const analysis = await analyzeAnswer(blob, `answer.${extension}`);
      // analyzeAnswer는 실제 녹음 경로에서만 호출되므로(타이핑 경로는 submitTypedAnswer가
      // 별도로 처리) metrics는 항상 채워져 있지만, 타입상 VoiceMetrics | null이라 안전하게 처리한다.
      const faceMetricsResult = summarizeFaceFrames(faceFramesRef.current, analysis.metrics?.duration_sec ?? 0);
      handleAnalysisReady(analysis, faceMetricsResult);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "답변 분석에 실패했습니다.");
      setStage("error");
    }
  };

  // 2026-08-05: 마이크/카메라 없이(또는 실제 녹음 없이) 최종 리포트 UI만 빨리 확인하고 싶을
  // 때 쓰는 개발용 우회 버튼. import.meta.env.DEV로 감싸서 프로덕션 빌드에는 아예 안 들어간다.
  // 세션 질문 없이 답변 1개짜리 더미로 바로 최종 화면(session-report)까지 건너뛴다.
  const fillDummyResultForDevTesting = () => {
    setSessionAnswers([
      {
        question: SAMPLE_QUESTIONS[0],
        result: {
          transcript:
            "이전 프로젝트에서 팀원과 API 설계 방향이 달라서 의견 차이가 있었습니다. 저는 회의를 잡아서 각자 장단점을 정리해서 공유했고, 결국 두 방식을 절충한 안으로 합의했습니다.",
          low_confidence_transcript: false,
          metrics: {
            duration_sec: 42,
            speaking_rate_chars_per_min: 280,
            pitch_mean_hz: 165.2,
            pitch_variation_hz: 18.4,
            silence_ratio: 0.12,
            long_pause_count: 1,
            volume_mean_rms: 0.08,
            volume_variation_rms: 0.03,
            // 개발용 더미 시계열 - 실제 답변 있는 것처럼 자연스러운 굴곡을 만들려고 사인파에
            // 약간의 흔들림을 섞었다(진짜 백엔드 값과 똑같을 필요는 없음, UI 확인용).
            timeline_seconds: Array.from({ length: 60 }, (_, i) => Number(((i / 59) * 42).toFixed(2))),
            timeline_pitch_hz: Array.from({ length: 60 }, (_, i) =>
              i % 11 === 3 ? null : Number((165 + Math.sin(i / 3) * 20 + Math.sin(i / 1.7) * 6).toFixed(1)),
            ),
            timeline_volume_rms: Array.from({ length: 60 }, (_, i) =>
              Number(Math.max(0, 0.08 + Math.sin(i / 4) * 0.04 + Math.sin(i / 1.3) * 0.015).toFixed(4)),
            ),
          },
        },
        faceMetrics: { blinkCount: 14, blinkRatePerMin: 22, headMovement: 12, frameCount: 300 },
      },
    ]);
    setStage("session-report");
  };

  // 2026-08-05: 녹음 답변(submitRecording)과 타이핑 답변(submitTypedAnswer) 둘 다 여기로
  // 모인다 - 방금 끝난 답변을 세션 목록에 쌓아두고, 세션의 마지막 질문이었으면 바로 최종
  // 종합 리포트 화면으로, 아니면 다음 질문으로 이어간다(텍스트 모드는 곧바로 다음 질문
  // 공개, 음성 모드는 10초 휴식 후 카운트다운). "질문마다 리포트 부르지 말고 다 받은 뒤
  // 한 번에 부르자"는 요청으로, 여기서는 Gemini를 호출하지 않는다 - 그건 session-report
  // 화면(SessionReportPanel)에 진입할 때 한 번만 일어난다.
  const finishAnswer = (analysis: AnswerAnalysis, faceMetricsResult: FaceMetrics | null) => {
    const isLastQuestion = sessionQuestions.length === 0 || sessionIndex >= sessionQuestions.length - 1;
    // 2026-08-05: question "state"가 아니라 currentQuestionRef.current를 쓴다 - 위 ref
    // 선언부 주석 참고(state를 쓰면 녹음 파이프라인의 오래된 클로저 때문에 한 질문 밀려서
    // 저장되는 버그가 있었다).
    setSessionAnswers((prev) => [...prev, { question: currentQuestionRef.current, result: analysis, faceMetrics: faceMetricsResult }]);
    setTypedAnswer("");

    if (isLastQuestion) {
      setStage("session-report");
      return;
    }

    const nextIndex = sessionIndex + 1;
    const next = sessionQuestions[nextIndex];
    setSessionIndex(nextIndex);
    setErrorMessage(null);
    setElapsedSec(0);

    if (answerMode === "text") {
      currentQuestionRef.current = next;
      setQuestion(next);
      setStage("typing");
      return;
    }

    // 2026-08-06: finishAnswer가 여기까지 오는 시점엔 10초 쉬는 시간이 이미 다 지나가 있다
    // (stopRecording에서 분석과 병렬로 미리 흘려보냈고, break useEffect/handleAnalysisReady가
    // 둘 다 끝난 걸 확인한 뒤에만 이 함수를 부른다) - 그래서 여기서 새로 "break"를 또
    // 시작하지 않고, 얼굴 추적을 재개하면서 곧장 3초 "질문 공개 직전" 카운트다운으로 넘어간다.
    if (landmarkerRef.current) startFaceTrackingLoop(landmarkerRef.current);
    setQuestionTextReady(false);
    pendingQuestionRef.current = next;
    setCountdownTotal(FIRST_COUNTDOWN_SECONDS);
    setCountdownValue(FIRST_COUNTDOWN_SECONDS);
    setStage("countdown");
  };

  // 2026-08-05: 녹음 대신 텍스트로 답변을 제출하는 경로 - STT/음성분석/얼굴분석을 아예
  // 거치지 않고 바로 finishAnswer로 간다. metrics를 null로 둬서, 렌더링 쪽이 "음성 지표가
  // 없는 답변"임을 구분할 수 있게 한다.
  const submitTypedAnswer = () => {
    const text = typedAnswer.trim();
    if (!text) return;
    setAnswerMode("text");
    finishAnswer({ transcript: text, low_confidence_transcript: false, metrics: null }, null);
  };

  // 2026-08-19: "질문 건너뛰기도 있었으면 좋겠다"는 요청으로 추가 - 녹음 중이던 내용은
  // 서버로 보내서 분석(STT/음성분석)하지 않고 그냥 "건너뛴 질문"이라는 표시만 남긴 채
  // 다음 질문으로 넘어간다. stopRecording과 달리 submitRecording(analyzeAnswer 호출)로
  // 이어지면 안 되므로, recorder.onstop 핸들러를 먼저 떼어낸 뒤 stop한다 - onstop이 그대로
  // 붙어있으면 방금 건너뛴 질문의 (짧고 의미 없을 가능성이 큰) 녹음이 그대로 분석돼버린다.
  // 세션 리포트(evaluation.py generate_session_report)는 각 답변의 transcript를 그대로
  // Gemini 프롬프트에 넣으므로, 빈 문자열 대신 "건너뛰었다"는 걸 명시한 문장을 넣어서
  // 모델이 "답변 없음"과 헷갈리지 않고 그대로 코멘트할 수 있게 한다.
  const skipCurrentQuestion = () => {
    if (isRecordingRef.current && mediaRecorderRef.current) {
      mediaRecorderRef.current.onstop = null;
      try {
        mediaRecorderRef.current.stop();
      } catch {
        // 이미 멈춘 상태 등은 무시 - 어차피 아래에서 상태를 정리한다.
      }
    }
    isRecordingRef.current = false;
    stopFaceLoop();
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }
    pendingAnalysisRef.current = null;
    breakCountdownDoneRef.current = false;
    finishAnswer(
      { transcript: "(사용자가 이 질문을 건너뛰었습니다)", low_confidence_transcript: false, metrics: null },
      null,
    );
  };

  // 세션을 완전히 종료하고 랜딩 화면으로 돌아간다 - 카메라/마이크 스트림도 이 시점에만 끈다.
  const endSession = () => {
    stopMeterLoop();
    stopFaceLoop();
    stopTtsAudio();
    window.speechSynthesis?.cancel();
    isRecordingRef.current = false;
    disconnectPhonePairing();
    stopStream();
    landmarkerRef.current = null;
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }
    setCameraReady(false);
    setSessionQuestions([]);
    setSessionIndex(0);
    setSessionAnswers([]);
    currentQuestionRef.current = "";
    pendingAnalysisRef.current = null;
    breakCountdownDoneRef.current = false;
    setQuestionTextReady(false);
    setQuestion("");
    setStage("start");
    setErrorMessage(null);
    setMicLevel(0);
    setElapsedSec(0);
    setAnswerMode("voice");
    setTypedAnswer("");
    setChatOnlyMode(false);
  };

  // 2026-08-11: "session-report" 화면(마지막 질문까지 다 끝나고 결과를 보는 화면)에
  // 도달하면 녹화는 이미 다 끝난 거라 카메라/마이크를 켜둘 이유가 없는데, 예전엔
  // endSession()을 사용자가 리포트 화면의 "처음으로" 버튼을 눌러야만 호출해서(1965번째 줄
  // 근처 SessionReportPanel의 onEndSession) 그 버튼을 안 누르고 리포트만 보고 있으면 캠이
  // 계속 켜진 채로 남아있었다("면접 끝나면 꺼져야 하는데 계속 켜져있다" 피드백). endSession()
  // 전체를 부르면 sessionAnswers 등 리포트가 필요로 하는 상태까지 지워버리니, 스트림/추적
  // 루프만 멈추는 부분집합(cancelDeviceTest와 같은 조합)만 여기서 따로 정리한다.
  useEffect(() => {
    if (stage !== "session-report") return;
    stopMeterLoop();
    stopFaceLoop();
    disconnectPhonePairing();
    stopStream();
    setCameraReady(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage]);

  // 질문 준비는 이미 끝난 상태에서 생긴 오류(마이크 권한 거부 등)면 device-check로,
  // 질문 준비 자체가 안 된 상태의 오류면 처음(start)으로 돌아간다.
  const retryAfterError = () => {
    setErrorMessage(null);
    setStage(sessionQuestions.length > 0 ? "device-check" : "start");
  };

  // 2026-08-07: "break"(답변 끝나고 쉬는 10초)도 추가했다 - 예전엔 이 단계에서 캠 화면이
  // display:none으로 통째로 사라졌다가 다음 질문 카운트다운에서 다시 나타나서 "화면이
  // 깜빡인다"는 피드백이 있었다. 카메라 자체는 계속 살아있는 스트림이라 굳이 안 보여줄
  // 이유가 없고, 아래 오버레이(showBreakOverlay)로 "지금은 녹화 안 함"만 표시하면 된다.
  const showVideoPreview =
    stage === "preparing" ||
    stage === "testing-mic" ||
    stage === "countdown" ||
    stage === "get-ready" ||
    stage === "recording" ||
    stage === "break";

  const hasSession = sessionQuestions.length > 0;
  // 2026-08-06: "countdown"(3초) 단계 자체는 아직 이전 질문 텍스트를 들고 있을 수 있어서
  // 무조건 포함시키면 안 되고, questionTextReady(질문이 실제로 공개된 시점부터 true)로
  // 판단한다 - TTS가 질문을 읽어주는 동안에도 텍스트가 화면에 보이게 하려는 목적.
  const questionRevealed =
    questionTextReady || stage === "get-ready" || stage === "recording" || stage === "analyzing" || stage === "typing";
  // 2026-08-13: "질문 듣기" 버튼 disabled 조건 - 별도 변수로 한 번만 계산해둔다. JSX 안에서
  // showVideoPreview/questionRevealed로 이미 좁혀진 stage 타입 위에 또 stage === "recording"을
  // 직접 비교하면(narrowing이 겹치는 지점에 따라) TS가 "겹치는 타입이 없다"고 오탐하는 경우가
  // 있어서, 좁혀지기 전에 미리 계산해 불리언 하나로만 참조한다.
  const questionAudioBusy = stage === "recording" || stage === "analyzing";

  return (
    <>
      <PageHeading
        eyebrow="Early Bird AI모의면접"
        title="AI모의면접"
        body={`시작하면 자기소개를 포함한 질문 ${questionCount}개가 순서대로 나옵니다. 질문은 공개 직전까지 보이지 않아서 실제 면접처럼 답변을 준비하고, 말투(속도·높낮이·침묵)와 표정 신호(눈 깜빡임·고개 움직임)를 함께 분석해 보여줍니다.`}
      />

      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>질문</h2>
            <p>
              {hasSession && stage !== "device-check"
                ? `질문 ${sessionIndex + 1} / ${sessionQuestions.length}`
                : `면접을 시작하면 질문 ${questionCount}개(자기소개 포함)가 순서대로 진행됩니다.`}
            </p>
          </div>
        </div>

        {/* 2026-08-13: "녹화 UI가 화상통화처럼 느껴지게 해달라"는 요청으로, 카메라를 쓰는
            단계(showVideoPreview)에서는 이 밋밋한 카드 대신 아래 interview-call-stage(면접관
            타일 + 내 카메라 타일)로 질문을 보여준다 - 타이핑 모드/분석 중처럼 카메라가 없는
            화면에서는 기존 카드를 그대로 쓴다. */}
        {questionRevealed && !showVideoPreview && (
          <div className="interview-question-card">
            <span className="interview-question-icon">
              <Sparkles size={19} />
            </span>
            <strong>{question}</strong>
            <button
              className="primary-button"
              onClick={() => speakQuestion()}
              type="button"
              disabled={questionAudioBusy}
              style={{ marginLeft: "auto", flex: "none" }}
            >
              <Volume2 size={16} /> 질문 듣기
            </button>
          </div>
        )}

        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "24px 0" }}>
          {/* 2026-08-13: 실제 화상 면접처럼 "면접관(고양이 마스코트, 말풍선으로 질문 표시)"과
              "나(카메라 미리보기)" 두 타일을 나란히 보여준다. 기존엔 카메라 미리보기 하나만
              덩그러니 있었는데, 옆에 면접관 타일을 두고 질문이 거기서 말풍선으로 나오게 하면
              실제 화상 면접 화면과 훨씬 비슷한 느낌을 준다. */}
          {showVideoPreview && (
            <div className="interview-call-stage">
              <div className="interview-call-tile interviewer-tile">
                <div className="interviewer-avatar-wrap">
                  {/* 2026-08-14: "차라리 편집하지 말고 영상통화처럼 그 영상을 그대로 쓰자"는
                      요청 - AI(lipsync.video)가 만들어준 실제 영상(mascot-interview-talking.mp4,
                      음성만 제거)을 배경 편집 없이 그대로 쓴다. 대신 컨테이너 크기를 고정해서
                      (interviewer-avatar-frame) 이전에 겪었던 "정지 이미지 ↔ 애니메이션 프레임을
                      바꿀 때 고양이 전체 크기가 같이 흔들리는" 문제가 재발하지 않게 했다 - 이미지/
                      영상 둘 다 이 고정 박스 안에서 object-fit: cover로 채워지므로 박스 자체는
                      절대 움직이지 않는다. 영상 자체의 어두운 배경은 실제 화상통화 타일처럼
                      보이게 컨테이너도 어두운 배경(candidate-tile과 톤 맞춤)으로 감쌌다. */}
                  <div className="interviewer-avatar-frame">
                    {isSpeaking ? (
                      <video
                        key="talking"
                        className="interviewer-avatar-media"
                        src="/mascot-interview-talking.mp4"
                        autoPlay
                        loop
                        muted
                        playsInline
                      />
                    ) : (
                      <img
                        src="/mascot-interview-face.png"
                        alt="AI 면접관"
                        className="interviewer-avatar-media interviewer-avatar-media-idle"
                      />
                    )}
                  </div>
                  {/* isSpeaking은 speakQuestionText가 실제로 재생 중인 구간(질문 자동 낭독 +
                      "다시 듣기" 수동 재생 모두 포함)과 정확히 일치한다. */}
                  {isSpeaking && (
                    <span className="interviewer-speaking-badge">
                      <Volume2 size={11} /> 말하는 중
                    </span>
                  )}
                </div>
                {questionRevealed && (
                  <div className="interviewer-speech-bubble">
                    <p>{question}</p>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => speakQuestion()}
                      disabled={questionAudioBusy}
                    >
                      <Volume2 size={13} /> 다시 듣기
                    </button>
                  </div>
                )}
                <span className="interview-call-tag">AI 면접관</span>
              </div>

              <div className="interview-call-tile candidate-tile">
                {/* 2026-08-07: 컨테이너 전체를 좌우 반전시킨다(video 하나만 반전하면 canvas에
                    그리는 얼굴 랜드마크 좌표가 화면상 얼굴 위치와 어긋나 버린다 - 부모를
                    통째로 뒤집으면 video/canvas가 같은 좌표계 그대로 유지된 채 화면에만
                    거울처럼 보인다). MediaPipe 얼굴 인식은 이 CSS 표시 변환과 무관하게
                    videoRef의 원본 프레임 데이터를 그대로 읽으므로 분석/녹화 결과에는 영향이
                    없다. */}
                <div className="candidate-video-frame" style={{ transform: "scaleX(-1)" }}>
                  <video
                    autoPlay
                    muted
                    playsInline
                    ref={videoRef}
                    style={{
                      width: "100%",
                      height: "100%",
                      borderRadius: 14,
                      background: "#111",
                      objectFit: "cover",
                    }}
                  />
                  <canvas
                    ref={canvasRef}
                    style={{
                      position: "absolute",
                      inset: 0,
                      width: "100%",
                      height: "100%",
                      pointerEvents: "none",
                    }}
                  />
                  {/* 2026-08-07: 쉬는 시간(break)엔 캠을 완전히 숨기는 대신 반투명 어두운 막만
                      덮어서 "지금은 녹화 중이 아님"을 표시한다 - 화면이 통째로 사라졌다
                      나타나는 깜빡임 대신 자연스러운 전환을 준다. */}
                  <div
                    style={{
                      position: "absolute",
                      inset: 0,
                      borderRadius: 14,
                      background: "rgba(15, 17, 26, 0.6)",
                      display: stage === "break" ? "block" : "none",
                      pointerEvents: "none",
                    }}
                  />
                </div>
                <span className="interview-call-tag">나</span>
              </div>
            </div>
          )}
          {showVideoPreview && (
            <>
              <span style={{ fontSize: 11, color: "#9098a7" }}>점선 타원 안에 얼굴을 맞춰주세요</span>
              {/* 2026-08-07: 영상이 서버에 저장/전송되는 걸로 오해할 수 있어서(실제로는
                  브라우저 안에서만 프레임을 읽어 얼굴 지표를 계산하고, 서버로는 답변 음성만
                  올라간다) 명시적으로 안내한다. */}
              <span style={{ fontSize: 11, color: "#9098a7" }}>영상은 저장되지 않습니다</span>
            </>
          )}

          {stage === "start" && (
            <div style={{ width: "100%", display: "flex", flexDirection: "column", alignItems: "center", gap: 14 }}>
              {/* 2026-08-06: 카드 모양(아이콘/제목/설명)은 그대로 두고, 카드 안 개별
                  "시작하기" 버튼은 없앤 뒤 카드 자체를 클릭해서 고르는 라디오 방식으로
                  바꿨다 - 선택된 카드는 아래 옵션 칩들과 똑같은 "선택됨(.active)" 파란
                  스타일로 바뀐다(배경색도 평소엔 아래 칩과 같은 연한 파랑으로 맞췄다).
                  실제 시작은 아래 옵션들 다음에 있는 단일 "모의면접 시작하기" 버튼이
                  현재 선택된 interviewMode를 보고 처리한다. */}
              <div className="interview-mode-grid">
                <button
                  type="button"
                  className={`interview-mode-card${interviewMode === "camera" ? " active" : ""}`}
                  onClick={() => setInterviewMode("camera")}
                >
                  <span className="interview-mode-icon">
                    <Mic size={22} />
                  </span>
                  <strong>카메라·마이크 모의면접</strong>
                  <p>자기소개를 포함한 질문 {questionCount}개가 순서대로 나옵니다. 말투(속도·높낮이·침묵)와 표정 신호까지 함께 분석해 드려요.</p>
                </button>
                <button
                  type="button"
                  className={`interview-mode-card${interviewMode === "chat" ? " active" : ""}`}
                  onClick={() => setInterviewMode("chat")}
                >
                  <span className="interview-mode-icon">
                    <MessageCircle size={22} />
                  </span>
                  <strong>채팅으로 연습하기</strong>
                  <p>카메라·마이크가 없어도 괜찮아요. 화면이 바로 면접 모드로 바뀌고, 제한시간 안에 답변을 입력하면 그 자리에서 피드백과 모범답안을 받을 수 있어요.</p>
                </button>
              </div>

              {/* 2026-08-19: "연습(범용 질문) 없애고 프로필 불러오기는 따로 만들어서, 프로필을
                  불러오면 실제 면접처럼 면접 유형/면접 분야를 비활성화해달라"는 요청으로 개편.
                  예전엔 "연습"/"프로필 불러오기" 두 칩 중 하나를 고르는 그룹이었는데, 이제
                  "프로필 불러오기" 하나만 있는 토글이다(안 누르면 기존 "연습"과 동일하게
                  면접 유형/분야를 직접 고른다 - 기본값은 그대로 questionSource="practice").
                  이 토글이 맨 위로 올라온 이유: 아래 두 그룹(면접 유형/면접 분야)의 활성화
                  여부가 이 값에 달려있어서, 사용자가 먼저 보고 정할 수 있게 순서를 바꿨다. */}
              <div className="interview-option-group">
                <span className="interview-option-label">질문 기준</span>
                <div className="interview-option-row">
                  <button
                    type="button"
                    className={`interview-option-chip${questionSource === "profile" ? " active" : ""}`}
                    onClick={() => {
                      const next = questionSource === "profile" ? "practice" : "profile";
                      setQuestionSource(next);
                      // 프로필 불러오기를 켜는 순간, 그 자리에서 직접 고른 면접 유형/분야는
                      // 초기화한다 - 안 그러면 buildSessionQuestions에서 이 값들이 프로필의
                      // job/techSummary보다 우선해버려서 "프로필 불러오기"가 무의미해진다.
                      if (next === "profile") {
                        setSelectedInterviewType(null);
                        setSelectedRole(null);
                      }
                    }}
                  >
                    프로필 불러오기
                  </button>
                </div>
                {questionSource === "profile" && (
                  <p style={{ marginTop: 10, fontSize: 12, color: "#9098a7" }}>
                    실제 면접처럼 면접 유형·분야를 직접 고르지 않고, 마이페이지에 입력한 목표
                    직무·기술 요약을 그대로 반영해서 질문을 만들어요.
                  </p>
                )}
                {questionSource === "profile" && !subscribed && (
                  <p className="account-alert" style={{ marginTop: 10 }}>
                    프로필 맞춤 질문은 구독자 전용 기능이에요. 구독하면 마이페이지에 입력해둔
                    목표 직무·기술 요약을 반영한 질문을 받을 수 있어요.{" "}
                    <Link to="/account" style={{ textDecoration: "underline", textUnderlineOffset: 3 }}>
                      맞춤 모의면접 보기
                    </Link>
                  </p>
                )}
                {questionSource === "profile" && subscribed && !careerJob && !careerTechSummary && (
                  <p className="account-alert" style={{ marginTop: 10 }}>
                    아직 프로필에 입력된 목표 직무·기술 요약이 없어요.{" "}
                    <Link to="/profile">프로필 작성하기</Link>
                  </p>
                )}
                {/* 2026-08-19: "프로필 불러오기가 된 건지 어떻게 아냐, 구독자는 불러와졌으면
                    '프로필을 불러왔어요!' 식으로 알려줘야 한다"는 요청으로 추가 - 구독 중이고
                    실제로 반영할 프로필 데이터(목표 직무 또는 기술 요약)가 있을 때만 보여준다
                    (subscribed && !careerJob && !careerTechSummary 분기와 상호 배타적). */}
                {questionSource === "profile" && subscribed && (careerJob || careerTechSummary) && (
                  <p className="account-alert" style={{ marginTop: 10 }}>
                    프로필을 불러왔어요! 마이페이지에 입력한 목표 직무·기술 요약을 반영해서
                    질문을 만들게요.
                    {careerJob && (
                      <>
                        {" "}
                        (목표 직무: <strong>{careerJob}</strong>)
                      </>
                    )}
                  </p>
                )}
              </div>

              {/* 2026-08-07: "역량/직무/인성 면접 유형도 고르게 하자" 요청으로 추가 - 인성/역량
                  계열 질문(팀 갈등, 강점/약점 등)은 지원자의 경험을 묻는 거라 분야가 달라도
                  질문 자체는 같아도 되지만, 직무(기술) 면접만 분야별로 내용이 달라야 한다는
                  게 핵심 아이디어. "전체"(선택 안 함)는 기존처럼 6개 카테고리를 다 순환한다.
                  2026-08-19: "프로필 불러오기" 중엔 실제 면접처럼 유형을 직접 못 고르게
                  비활성화한다(위 토글 참고). */}
              <div className={`interview-option-group${questionSource === "profile" ? " interview-option-group-disabled" : ""}`}>
                <span className="interview-option-label">
                  면접 유형{questionSource === "profile" && <span style={{ fontWeight: 400, color: "#9098a7" }}> (프로필 기준으로 자동 결정)</span>}
                </span>
                <div className="interview-option-row">
                  <button
                    type="button"
                    disabled={questionSource === "profile"}
                    className={`interview-option-chip${selectedInterviewType === "" ? " active" : ""}`}
                    onClick={() => setSelectedInterviewType("")}
                  >
                    전체
                  </button>
                  {INTERVIEW_TYPE_OPTIONS.map(([type]) => (
                    <button
                      key={type}
                      type="button"
                      disabled={questionSource === "profile"}
                      className={`interview-option-chip${selectedInterviewType === type ? " active" : ""}`}
                      onClick={() => setSelectedInterviewType(type)}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              {/* 2026-08-06: 분야를 고르면 그 분야에 맞는 질문이 나온다. 드롭다운 대신
                  라디오버튼처럼 클릭하는 카드(칩) 형태로 해달라는 요청으로 select를 버튼
                  그룹으로 바꿨다. 2026-08-19: "프로필 불러오기" 중엔 위 면접 유형과 같은
                  이유로 비활성화한다. */}
              <div className={`interview-option-group${questionSource === "profile" ? " interview-option-group-disabled" : ""}`}>
                <span className="interview-option-label">
                  면접 분야{questionSource === "profile" && <span style={{ fontWeight: 400, color: "#9098a7" }}> (프로필 기준으로 자동 결정)</span>}
                </span>
                {/* 2026-08-07: 선택 안 함 + 분야 5개 = 6개라 auto-fit이 5+1로 어색하게
                    쪼개졌다 - 3열로 고정해서 3+3으로 깔끔하게 떨어지게 했다. */}
                <div className="interview-option-row" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
                  <button
                    type="button"
                    disabled={questionSource === "profile"}
                    className={`interview-option-chip${selectedRole === "" ? " active" : ""}`}
                    onClick={() => setSelectedRole("")}
                  >
                    선택 안 함
                  </button>
                  {INTERVIEW_ROLE_OPTIONS.map(([code, label]) => (
                    <button
                      key={code}
                      type="button"
                      disabled={questionSource === "profile"}
                      className={`interview-option-chip${selectedRole === code ? " active" : ""}`}
                      onClick={() => setSelectedRole(code)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="interview-option-group">
                <span className="interview-option-label">질문 개수</span>
                <div className="interview-option-row">
                  {QUESTION_COUNT_OPTIONS.map((count) => (
                    <button
                      key={count}
                      type="button"
                      className={`interview-option-chip${questionCount === count ? " active" : ""}`}
                      onClick={() => setQuestionCount(count)}
                    >
                      {count}개
                    </button>
                  ))}
                </div>
              </div>

              {/* 2026-08-06: 클라우드 TTS 키가 설정돼 있을 때만(ttsVoiceOptions가 비어있지
                  않을 때만) 노출한다 - 키가 없는 환경에선 이 선택 자체가 의미 없다(브라우저
                  기본 TTS만 쓰게 됨). "미리 듣기"로 바로 들어보고 마음에 드는 걸 고를 수 있게. */}
              {ttsVoiceOptions.length > 0 && (
                <div className="interview-option-group">
                  <span className="interview-option-label">질문 읽어주는 목소리</span>
                  <div className="interview-option-row">
                    {ttsVoiceOptions.map((v) => (
                      <button
                        key={v.id}
                        type="button"
                        className={`interview-option-chip${selectedTtsVoice === v.id ? " active" : ""}`}
                        onClick={() => setSelectedTtsVoice(v.id)}
                      >
                        {v.label}
                      </button>
                    ))}
                  </div>
                  <button
                    type="button"
                    className="text-button"
                    style={{ fontSize: 11 }}
                    onClick={() => speakQuestionText("안녕하세요, 만나서 반갑습니다. 이렇게 질문을 읽어드릴게요.", () => {})}
                  >
                    <Volume2 size={12} /> 미리 듣기
                  </button>
                </div>
              )}

              <button
                className="primary-button"
                onClick={() => (interviewMode === "camera" ? startSession() : startChatModeSession())}
                type="button"
                style={{ fontSize: 15, padding: "14px 32px" }}
              >
                <Sparkles size={18} /> 모의면접 시작하기
              </button>

              {import.meta.env.DEV && (
                <button className="text-button" onClick={fillDummyResultForDevTesting} type="button" style={{ fontSize: 11 }}>
                  (개발용) 마이크 없이 더미 결과로 리포트 UI 확인
                </button>
              )}
            </div>
          )}

          {stage === "device-check" && (
            <>
              {sessionQuestions.length === 0 ? (
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
                  <LoadingBuddy size={44} />
                  <p style={{ margin: 0, color: "#465067", fontSize: 13, textAlign: "center" }}>
                    질문을 백그라운드에서 준비하고 있어요 - 먼저 마이크와 카메라부터 확인해 주세요.
                  </p>
                </div>
              ) : (
                <p style={{ margin: 0, color: "#2e9e5b", fontSize: 13, textAlign: "center" }}>
                  {`질문 ${sessionQuestions.length}개 준비 완료! 마이크와 카메라를 확인해 주세요.`}
                </p>
              )}
              <button className="primary-button" onClick={() => void startDeviceTest()} type="button">
                <Mic size={16} /> 마이크·카메라 테스트
              </button>
              <button className="text-button" onClick={() => setPairingPanelOpen(true)} type="button">
                <Camera size={15} /> 폰을 카메라로 연결
              </button>
              {pairingPanelOpen && (
                <PhoneCameraPairingPanel
                  onRemoteStream={(stream) => void usePhoneCameraStream(stream)}
                  onConnected={({ disconnect, sendInterviewState }) => {
                    phonePairDisconnectRef.current = disconnect;
                    phonePairStateRef.current = sendInterviewState;
                  }}
                  onClose={() => setPairingPanelOpen(false)}
                />
              )}
              <button
                className="text-button"
                onClick={startTypingForSession}
                type="button"
                disabled={sessionQuestions.length === 0}
                style={{ fontSize: 10, color: "#b0b6c0", fontWeight: 700, opacity: sessionQuestions.length === 0 ? 0.5 : 1 }}
              >
                마이크/캠을 사용할 수 없어요
              </button>
            </>
          )}

          {stage === "typing" && (
            <div style={{ width: "100%", maxWidth: 480, display: "flex", flexDirection: "column", gap: 10 }}>
              {!question ? (
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
                  <LoadingBuddy size={44} />
                  <p style={{ margin: 0, color: "#9098a7", fontSize: 13 }}>질문을 준비하고 있어요...</p>
                </div>
              ) : (
                <>
                  {/* 2026-08-06: "질문도 제한시간 안에 고민 안 하고 칠 수 있게" 요청으로 추가한
                      게이지 바 타이머 - 90초에서 시작해 줄어들고, 남은 시간이 얼마 안 남으면
                      색이 빨간색으로 바뀌어서 압박감을 준다. 0이 되면(입력한 내용이 있을 때만)
                      위쪽 useEffect가 자동으로 제출한다. */}
                  <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#9098a7" }}>
                      <span>답변 제한시간</span>
                      <span style={{ fontWeight: 800, color: typingSecondsLeft <= 10 ? "#e0524c" : "#465067" }}>
                        {typingSecondsLeft}초
                      </span>
                    </div>
                    <div style={{ width: "100%", height: 8, borderRadius: 999, background: "#eef0f6", overflow: "hidden" }}>
                      <div
                        style={{
                          width: `${(typingSecondsLeft / TYPING_TIME_LIMIT_SEC) * 100}%`,
                          height: "100%",
                          borderRadius: 999,
                          background: typingSecondsLeft <= 10 ? "#e0524c" : typingSecondsLeft <= 30 ? "#e0a83f" : "#596ff3",
                          transition: "width 1s linear, background .3s",
                        }}
                      />
                    </div>
                  </div>

                  <textarea
                    value={typedAnswer}
                    onChange={(e) => setTypedAnswer(e.target.value)}
                    placeholder="답변을 텍스트로 입력해 주세요..."
                    rows={6}
                    autoFocus
                    style={{
                      width: "100%",
                      border: "1px solid #dfe4ec",
                      borderRadius: 10,
                      padding: "10px 12px",
                      font: "inherit",
                      fontSize: 13,
                      color: "#293349",
                      resize: "vertical",
                    }}
                  />
                  <div style={{ display: "flex", gap: 10 }}>
                    <button className="primary-button" onClick={submitTypedAnswer} type="button" disabled={!typedAnswer.trim()}>
                      답변 제출
                    </button>
                    {/* 2026-08-19: "질문 건너뛰기" 요청으로 추가 - 답변을 안 쓰고 다음 질문으로
                        바로 넘어간다(취소와 달리 세션 자체는 계속 진행됨). */}
                    <button className="text-button" onClick={skipCurrentQuestion} type="button">
                      <SkipForward size={14} /> 이 질문 건너뛰기
                    </button>
                    <button
                      className="text-button"
                      onClick={() => {
                        if (chatOnlyMode) {
                          endSession();
                        } else {
                          setStage("device-check");
                          setTypedAnswer("");
                        }
                      }}
                      type="button"
                    >
                      취소
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

          {stage === "preparing" && (
            <strong style={{ color: "#9098a7", fontSize: 13 }}>
              {cameraReady ? "마이크 확인 중..." : "얼굴 인식 모델 준비 중... (처음 한 번만 몇 초 걸림)"}
            </strong>
          )}

          {stage === "testing-mic" && (
            <>
              <canvas
                ref={waveformCanvasRef}
                style={{
                  width: "100%",
                  maxWidth: 320,
                  height: 56,
                  borderRadius: 8,
                  background: "#eef0f6",
                  border: micLevel < 8 ? "1px solid #e05252" : "1px solid transparent",
                }}
              />
              <span style={{ fontSize: 11, color: micLevel < 8 ? "#c0392b" : "#9098a7" }}>
                {micLevel < 8 ? "소리가 거의 안 잡혀요 - 마이크에 더 가까이서 말해보세요" : "마이크가 소리를 잡고 있어요"}
              </span>
              <div style={{ display: "flex", gap: 10 }}>
                <button className="primary-button" onClick={beginInterviewCountdown} type="button" disabled={sessionQuestions.length === 0}>
                  {sessionQuestions.length === 0 ? (
                    <>
                      <LoaderCircle className="spin" size={16} /> 질문 준비 중...
                    </>
                  ) : (
                    <>
                      <Mic size={16} /> 모의면접 시작하기
                    </>
                  )}
                </button>
                <button className="text-button" onClick={cancelDeviceTest} type="button">
                  취소
                </button>
              </div>
            </>
          )}

          {stage === "break" && (
            <>
              <strong style={{ fontSize: 14, color: "#8a93a3", fontWeight: 700 }}>잠시 쉬어가세요</strong>
              <span style={{ fontSize: 12, color: "#9098a7" }}>
                {/* 2026-08-06: 방금 답변 분석이 이 10초 쉬는 시간과 백그라운드에서 같이 도는 중이라,
                    드물게 분석이 더 오래 걸리면 카운트다운이 0에서 잠깐 멈춘 채 대기할 수 있다 -
                    그 순간엔 "0초"보다 자연스러운 문구로 바꿔준다. */}
                {countdownValue > 0 ? `이 시간은 분석하지 않아요 - 다음 질문까지 ${countdownValue}초` : "답변을 정리하고 있어요, 곧 다음 질문으로 넘어갈게요..."}
              </span>
              <CountdownRing value={countdownValue} total={countdownTotal} color="#c3c9d4" />
            </>
          )}

          {stage === "countdown" && (
            <>
              <strong style={{ fontSize: 14, color: "#596ff3", fontWeight: 700 }}>정면을 응시해주세요</strong>
              <CountdownRing value={countdownValue} total={countdownTotal} color="#596ff3" />
            </>
          )}

          {stage === "get-ready" && (
            <>
              <strong style={{ fontSize: 14, color: "#596ff3", fontWeight: 700 }}>곧 녹화가 시작됩니다. 정면을 응시해주세요</strong>
              <CountdownRing value={countdownValue} total={countdownTotal} color="#596ff3" />
            </>
          )}

          {stage === "recording" && (
            <>
              <Mic color="#e05252" size={28} />
              <strong style={{ color: "#293349", fontSize: 14 }}>녹음 중...</strong>
              <strong
                style={{
                  fontSize: 20,
                  fontVariantNumeric: "tabular-nums",
                  color:
                    elapsedSec >= RECOMMENDED_MIN_SEC && elapsedSec <= RECOMMENDED_MAX_SEC
                      ? "#2e9e5b"
                      : elapsedSec > RECOMMENDED_MAX_SEC
                        ? "#d98c00"
                        : "#293349",
                }}
              >
                {String(Math.floor(elapsedSec / 60)).padStart(2, "0")}:{String(elapsedSec % 60).padStart(2, "0")}
              </strong>
              <span style={{ fontSize: 11, color: "#9098a7" }}>권장 답변 길이: 30초~1분</span>
              <div style={{ display: "flex", gap: 10 }}>
                <button className="primary-button" onClick={stopRecording} type="button">
                  <Square size={14} /> 답변 완료
                </button>
                {/* 2026-08-19: "질문 건너뛰기" 요청으로 추가 - 지금까지 녹음한 내용은 분석하지
                    않고 버리고(skipCurrentQuestion 참고) 바로 다음 질문으로 넘어간다. */}
                <button className="text-button" onClick={skipCurrentQuestion} type="button">
                  <SkipForward size={14} /> 건너뛰기
                </button>
              </div>
            </>
          )}

          {stage === "analyzing" && <strong style={{ color: "#9098a7", fontSize: 13 }}>답변 분석 중...</strong>}

          {stage === "error" && errorMessage && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, color: "#c0392b", fontSize: 12, textAlign: "center" }}>
              <AlertCircle size={16} />
              <span>{errorMessage}</span>
            </div>
          )}

          {stage === "error" && (
            <button className="text-button" onClick={retryAfterError} type="button">
              다시 시도
            </button>
          )}
        </div>
      </section>

      {stage === "session-report" && (
        <SessionReportPanel
          answers={sessionAnswers}
          onEndSession={endSession}
          role={selectedRole || null}
          interviewMode={interviewMode}
          interviewType={selectedInterviewType || null}
        />
      )}
    </>
  );
}
