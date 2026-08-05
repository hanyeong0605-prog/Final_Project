import { useEffect, useRef, useState } from "react";
import { AlertCircle, LoaderCircle, Mic, RotateCcw, Square, Volume2 } from "lucide-react";
import { analyzeAnswer, evaluateAnswer, fetchNextQuestion } from "../features/mock-interview/api/mockInterviewApi";
import { FACE_OVAL_INDICES, loadFaceLandmarker, sampleFrame, summarizeFaceFrames } from "../features/mock-interview/lib/faceAnalysis";
import type { FaceFrameSample, FaceMetrics } from "../features/mock-interview/lib/faceAnalysis";
import type { AnswerAnalysis } from "../features/mock-interview/model/mockInterview.types";
import { PageHeading } from "../shared/components/PageHeading";
import { RangeGauge } from "../shared/components/RangeGauge";

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

function pickFallbackQuestion(exclude: string): string {
  const others = SAMPLE_QUESTIONS.filter((q) => q !== exclude);
  return others[Math.floor(Math.random() * others.length)] ?? SAMPLE_QUESTIONS[0];
}

type Stage = "idle" | "preparing" | "testing-mic" | "get-ready" | "recording" | "analyzing" | "result" | "error";

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

const metricLabels: {
  key: keyof AnswerAnalysis["metrics"];
  label: string;
  format: (value: number) => string;
  hint?: (value: number) => string;
  noBaseline?: boolean;
  // 2026-08-05: "정상 범위" 기준이 있는 지표에만 게이지 바를 그린다 - 값 자체의 단위로
  // min/max(막대 전체 스케일)와 goodMin/goodMax(양호 구간)를 넣는다. 기준이 없는 지표
  // (noBaseline: true인 것들)는 애초에 "정상 구간"이라는 게 없어서 게이지를 안 그린다.
  gauge?: { min: number; max: number; goodMin: number; goodMax: number };
}[] = [
  { key: "duration_sec", label: "답변 길이", format: (v) => `${v.toFixed(1)}초` },
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
  },
  { key: "pitch_mean_hz", label: "평균 음높이", format: (v) => `${v.toFixed(0)}Hz` },
  { key: "pitch_variation_hz", label: "음높이 변동폭", format: (v) => `${v.toFixed(0)}Hz`, noBaseline: true },
  {
    key: "silence_ratio",
    label: "침묵 비율",
    format: (v) => `${(v * 100).toFixed(1)}%`,
    hint: (v) => (v * 100 > 30 ? "침묵 비율이 다소 높아요. 답변이 자주 끊겼을 수 있어요." : "적절한 수준의 침묵 비율이에요."),
    // silence_ratio는 0~1 원시값이라 게이지도 같은 단위(0~1, 양호 구간 0~0.3)로 맞췄다.
    gauge: { min: 0, max: 1, goodMin: 0, goodMax: 0.3 },
  },
  {
    key: "long_pause_count",
    label: "긴 침묵 횟수",
    format: (v) => `${v}회`,
    hint: (v) => (v === 0 ? "긴 침묵 없이 이어갔어요." : `${v}번 길게 끊겼어요.`),
  },
  { key: "volume_variation_rms", label: "음량 떨림 정도", format: (v) => v.toFixed(4), noBaseline: true },
];

// 참고 기준 자체가 없는 지표들 - 카드 아래 이 문구를 공통으로 보여준다.
const NO_BASELINE_HINT = "비교 기준 없음 - 여러 번 연습해서 평소 값과 비교해 보세요.";

// blinkCount: 녹음하는 동안 실제로 센 깜빡임 횟수(그대로).
// blinkRatePerMin: 그 횟수를 "1분 동안 이 속도가 유지됐다면"으로 환산한 값 -
// 답변이 짧으면 실제 횟수보다 훨씬 커 보일 수 있어서(예: 6초에 3회 -> 분당 30회),
// 반드시 blinkCount와 나란히 보여줘서 오해가 없게 한다.
const faceMetricLabels: { key: keyof FaceMetrics; label: string; format: (value: number) => string; noBaseline?: boolean }[] = [
  { key: "blinkCount", label: "실제 깜빡임 횟수", format: (v) => `${v}회` },
  { key: "blinkRatePerMin", label: "분당 깜빡임 (환산)", format: (v) => `${v}회/분`, noBaseline: true },
  { key: "headMovement", label: "고개 움직임 정도", format: (v) => `${v}/100`, noBaseline: true },
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

export function MockInterviewPage() {
  const [question, setQuestion] = useState(() => SAMPLE_QUESTIONS[0]);
  const [questionLoading, setQuestionLoading] = useState(false);
  const [stage, setStage] = useState<Stage>("idle");
  const [result, setResult] = useState<AnswerAnalysis | null>(null);
  const [faceMetrics, setFaceMetrics] = useState<FaceMetrics | null>(null);
  // 2026-08-05: 종합 평가(evaluateAnswer)는 Gemini 호출이라 몇 초 걸릴 수 있어서, result 화면
  // 자체는 metrics만으로 먼저 보여주고 report는 별도 로딩 상태로 나중에 채워 넣는다 -
  // "답변 분석" 자체가 리포트 생성 때문에 지연되면 안 된다.
  const [report, setReport] = useState<string | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [micLevel, setMicLevel] = useState(0);
  const [cameraReady, setCameraReady] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const rafIdRef = useRef<number | null>(null);
  const faceRafIdRef = useRef<number | null>(null);
  const faceFramesRef = useRef<FaceFrameSample[]>([]);
  const isRecordingRef = useRef(false);
  const timerIdRef = useRef<number | null>(null);
  // 2026-08-05: 이번 세션에서 몇 번째 질문인지 세는 용도(0부터 시작) - 3개 중 1개는 무조건
  // 자기소개로 고정하기 위해 필요하다. 컴포넌트가 다시 마운트되면(면접 페이지 재진입) 0부터
  // 다시 센다.
  const questionCountRef = useRef(0);
  // 2026-08-05: 모델 생성이 6~8초씩 걸려서 "다음 질문"을 누른 시점에 요청을 시작하면 매번
  // 그만큼 기다려야 했다. 대신 지금 질문을 보여주는 즉시 다음 질문을 백그라운드로 미리
  // 만들어두고(prefetch), 실제로 "다음 질문"을 누르는 시점엔 이미 준비된 걸 즉시 보여준다.
  const prefetchRef = useRef<{ index: number; promise: Promise<string> } | null>(null);
  // 2026-08-05: React 18 StrictMode(main.tsx)는 개발 모드에서 마운트 이펙트를 일부러
  // 두 번 실행한다(부작용이 안전하게 반복 가능한지 검증하려는 의도). 아래 초기 로딩
  // useEffect가 두 번 실행되면 loadNextQuestion()이 연달아 두 번 불려서 - 1번째 호출로
  // 자기소개 질문이 뜨고 prefetch(다음 질문)가 백그라운드로 걸리는데, 곧바로 2번째
  // 호출이 같은 prefetch 슬롯을 "자기 차례"로 인식하고 그 프라미스가 끝나길 기다렸다가
  // 끝나는 순간 question/stage를 통째로 덮어써버렸다 - "카메라 앵글 맞추는 중에 갑자기
  // 다음 질문으로 바뀌는" 버그의 원인이었다(HMR 재마운트가 아니라 StrictMode 이중 실행).
  // 이 ref로 "진짜 처음 한 번"만 실행되게 막는다(프로덕션 빌드는 StrictMode 이중 실행이
  // 애초에 없어서 원래도 영향 없었음 - 개발 중에만 재현되던 버그).
  const hasInitialLoadRef = useRef(false);

  // 30초~1분 정도가 일반적인 면접 답변 권장 길이라는 참고 자료 기준 - 절대 기준은
  // 아니고, 감 잡는 용도로만 색을 살짝 바꿔 보여준다.
  const RECOMMENDED_MIN_SEC = 30;
  const RECOMMENDED_MAX_SEC = 60;

  // text를 인자로 받는 이유: setQuestion 직후 곧바로 읽어줘야 할 때, state 업데이트가
  // 비동기라 클로저 안의 question이 아직 이전 값일 수 있다 - 방금 정한 새 질문 텍스트를
  // 직접 넘겨받아서 그걸 읽는다. 버튼에서 수동으로 다시 듣기 할 땐 인자 없이 현재 question을 쓴다.
  const speakQuestion = (text: string = question) => {
    if (!("speechSynthesis" in window)) return;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ko-KR";
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  };

  // 2026-08-04: ai-server의 KoGPT2+LoRA 질문 생성 API를 부른다. 모델이 아직 안 떠 있거나
  // (503, RuntimeError from question_generator.py) 네트워크 오류가 나면 SAMPLE_QUESTIONS로
  // 폴백해서 화면이 완전히 막히지 않게 한다.
  //
  // 2026-08-05: 3개 중 1개(1, 4, 7번째...)는 모델을 아예 호출하지 않고 무조건 자기소개로
  // 고정한다 - 학습 데이터 특성상 생성 모델이 "자기소개해주세요"류를 알아서 규칙적으로
  // 만들어내지는 않아서, 실제 면접처럼 주기적으로 짚고 넘어가려면 강제로 끼워 넣는 게 낫다.
  // index를 인자로 받아 순수하게 "그 순번의 질문이 뭐가 되어야 하는지"만 반환한다(state를
  // 직접 안 건드림) - prefetch(미리 만들어두기)에서도 그대로 재사용하기 위함.
  const resolveQuestion = async (index: number, excludeForFallback: string): Promise<string> => {
    if (index % 3 === 0) return SELF_INTRO_QUESTION;
    try {
      const res = await fetchNextQuestion();
      return res.question;
    } catch {
      return pickFallbackQuestion(excludeForFallback);
    }
  };

  const schedulePrefetch = (index: number, excludeForFallback: string) => {
    prefetchRef.current = { index, promise: resolveQuestion(index, excludeForFallback) };
  };

  const loadNextQuestion = async () => {
    const index = questionCountRef.current;
    questionCountRef.current += 1;

    setErrorMessage(null);
    setQuestionLoading(true);

    let next: string;
    if (prefetchRef.current && prefetchRef.current.index === index) {
      // 미리 준비해둔 게 있으면 그걸 쓴다 - 이미 끝났으면 사실상 즉시, 너무 빨리 눌러서
      // 아직 안 끝났으면 그 프라미스가 끝날 때까지만 기다린다(그래도 새로 요청 두 번
      // 보내는 것보단 낫다).
      next = await prefetchRef.current.promise;
    } else {
      next = await resolveQuestion(index, question);
    }
    prefetchRef.current = null;
    setQuestionLoading(false);

    setQuestion(next);
    setStage("idle");
    setResult(null);
    setFaceMetrics(null);
    setReport(null);
    setReportLoading(false);
    setErrorMessage(null);
    speakQuestion(next);

    // 지금 질문을 보여주자마자 다음 질문을 백그라운드로 미리 만들어둔다 - 답변 준비/녹음/
    // 결과 확인하는 동안(보통 수십 초 이상) 시간이 충분해서 웬만하면 미리 끝나 있다.
    schedulePrefetch(questionCountRef.current, next);
  };

  const pickNextQuestion = () => {
    void loadNextQuestion();
  };

  // 첫 진입 시에도 정적 배열이 아니라 실제 생성된 질문으로 시작하고, 자동으로 읽어준다
  // ("면접관처럼 질문을 던지는" 게 원래 목표였어서 - 사용자가 매번 "질문 듣기"를 눌러야
  // 하는 건 그 취지에 안 맞는다).
  useEffect(() => {
    if (hasInitialLoadRef.current) return;
    hasInitialLoadRef.current = true;
    void loadNextQuestion();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stopMeterLoop = () => {
    if (rafIdRef.current !== null) cancelAnimationFrame(rafIdRef.current);
    rafIdRef.current = null;
    void audioContextRef.current?.close();
    audioContextRef.current = null;
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
    setErrorMessage(null);
    setStage("preparing");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { width: 640, height: 480 } });
      streamRef.current = stream;

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }

      // 얼굴 인식 모델(WASM+모델 파일)을 CDN에서 받아오는 데 몇 초 걸릴 수 있어서
      // "준비 중" 단계로 따로 보여준다.
      const landmarker = await loadFaceLandmarker();
      setCameraReady(true);
      startFaceTrackingLoop(landmarker);

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
        for (const value of data) {
          const centered = Math.abs((value - 128) / 128);
          if (centered > peak) peak = centered;
        }
        setMicLevel(Math.min(100, Math.round(peak * 250)));
        rafIdRef.current = requestAnimationFrame(tick);
      };
      tick();

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
    stopStream();
    setCameraReady(false);
    setStage("idle");
  };

  const GET_READY_MS = 1500;

  // 버튼 누르자마자 녹음을 시작하면 말할 준비가 안 된 채로 앞부분이 침묵으로 날아가는
  // 경우가 많아서(테스트 중 실제로 겪음), 짧게 준비 시간을 준 다음 녹음을 시작한다.
  const startRecording = () => {
    stopMeterLoop();
    if (!streamRef.current) {
      setErrorMessage("마이크/카메라 스트림을 찾을 수 없습니다. 다시 시도해 주세요.");
      setStage("error");
      return;
    }
    setStage("get-ready");
    window.setTimeout(beginActualRecording, GET_READY_MS);
  };

  const beginActualRecording = () => {
    const stream = streamRef.current;
    if (!stream) {
      setErrorMessage("마이크/카메라 스트림을 찾을 수 없습니다. 다시 시도해 주세요.");
      setStage("error");
      return;
    }

    // STT 서버에는 오디오만 보내면 되니, 녹음 자체는 오디오 트랙만 따로 담아서 만든다
    // (영상까지 녹화해서 올리면 용량도 크고 서버에 얼굴 영상을 보내는 셈이 되어버림).
    const audioOnlyStream = new MediaStream(stream.getAudioTracks());
    chunksRef.current = [];
    const recorder = new MediaRecorder(audioOnlyStream);
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunksRef.current.push(event.data);
    };
    recorder.onstop = () => void submitRecording();

    mediaRecorderRef.current = recorder;
    recorder.start();

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

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
    isRecordingRef.current = false;
    stopFaceLoop();
    stopStream();
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }
    setStage("analyzing");
  };

  const submitRecording = async () => {
    try {
      const blob = new Blob(chunksRef.current, { type: "audio/webm" });
      const analysis = await analyzeAnswer(blob, "answer.webm");
      const faceMetricsResult = summarizeFaceFrames(faceFramesRef.current, analysis.metrics.duration_sec);
      setResult(analysis);
      setFaceMetrics(faceMetricsResult);
      setReport(null);
      setStage("result");
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "답변 분석에 실패했습니다.");
      setStage("error");
    }
  };

  // 2026-08-05: 원래는 답변 분석 직후 자동으로 호출했는데, Gemini 호출이 답변마다 나가는 게
  // (질문 다듬기 1회 + 리포트 1회) 아까워서 - 사용자가 실제로 보고 싶을 때만 버튼으로 호출하도록
  // 바꿨다. 다시하기를 누르고 리포트를 안 보면 호출 자체가 안 나간다.
  const loadReport = async () => {
    if (!result) return;
    setReportLoading(true);
    try {
      const evaluation = await evaluateAnswer(question, result.transcript, result.metrics, faceMetrics);
      setReport(evaluation.report);
    } catch (error) {
      setReport(error instanceof Error ? error.message : "종합 평가를 불러오지 못했습니다.");
    } finally {
      setReportLoading(false);
    }
  };

  const reset = () => {
    stopMeterLoop();
    stopFaceLoop();
    isRecordingRef.current = false;
    stopStream();
    if (timerIdRef.current !== null) {
      window.clearInterval(timerIdRef.current);
      timerIdRef.current = null;
    }
    setCameraReady(false);
    setStage("idle");
    setResult(null);
    setFaceMetrics(null);
    setReport(null);
    setReportLoading(false);
    setErrorMessage(null);
    setMicLevel(0);
    setElapsedSec(0);
  };

  // 2026-08-05: "다른 질문"/"질문 듣기" 버튼이 stage와 무관하게 항상 클릭 가능했다 -
  // 답변을 녹음하거나("recording") 방금 녹음한 답변을 분석하는("analyzing") 도중에 이
  // 버튼을 누르면 loadNextQuestion()이 그대로 실행돼서 question/stage가 즉시 바뀌어
  // 버렸다("분석하다가 다음 질문으로 넘어가는" 것처럼 보이는 원인). 결과 화면("result")도
  // 마찬가지로 이 버튼 대신 "다시 하기"를 쓰는 흐름이라 막는다.
  const isAnswerInProgress = stage === "recording" || stage === "analyzing" || stage === "result";

  const showVideoPreview = stage === "preparing" || stage === "testing-mic" || stage === "get-ready" || stage === "recording";

  return (
    <>
      <PageHeading
        eyebrow="Early Bird 모의면접"
        title="모의면접"
        body="질문을 듣고 답변을 녹음하면, 답변 내용과 말투(속도·높낮이·침묵), 화면에 보이는 표정 신호(눈 깜빡임·고개 움직임)를 함께 분석해 보여줍니다."
      />

      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>질문</h2>
            <p>버튼을 눌러 질문을 음성으로 들을 수 있습니다.</p>
          </div>
          <button
            className="text-button"
            onClick={pickNextQuestion}
            type="button"
            disabled={questionLoading || isAnswerInProgress}
          >
            {questionLoading ? <LoaderCircle className="spin" size={13} /> : <RotateCcw size={13} />} 다른 질문
          </button>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "8px 0 24px" }}>
          <button
            className="primary-button"
            onClick={() => speakQuestion()}
            type="button"
            disabled={questionLoading || isAnswerInProgress}
          >
            <Volume2 size={16} /> 질문 듣기
          </button>
          {questionLoading ? (
            <span style={{ display: "flex", alignItems: "center", gap: 8, color: "#6a7383", fontSize: 14 }}>
              <LoaderCircle className="spin" size={16} /> 질문을 준비하는 중...
            </span>
          ) : (
            <strong style={{ color: "#293349", fontSize: 14 }}>{question}</strong>
          )}
        </div>

        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "24px 0" }}>
          <div style={{ position: "relative", display: showVideoPreview ? "block" : "none", width: 480, height: 360, maxWidth: "90vw" }}>
            <video
              autoPlay
              muted
              playsInline
              ref={videoRef}
              style={{
                width: "100%",
                height: "100%",
                borderRadius: 10,
                background: "#111",
                objectFit: "cover",
                transform: "scaleX(-1)",
              }}
            />
            <canvas
              ref={canvasRef}
              style={{
                position: "absolute",
                inset: 0,
                width: "100%",
                height: "100%",
                transform: "scaleX(-1)",
                pointerEvents: "none",
              }}
            />
          </div>
          {showVideoPreview && (
            <span style={{ fontSize: 11, color: "#9098a7" }}>점선 타원 안에 얼굴을 맞춰주세요</span>
          )}

          {stage === "idle" && (
            <button className="primary-button" onClick={startDeviceTest} type="button">
              <Mic size={16} /> 마이크·카메라 테스트
            </button>
          )}

          {stage === "preparing" && (
            <strong style={{ color: "#9098a7", fontSize: 13 }}>
              {cameraReady ? "마이크 확인 중..." : "얼굴 인식 모델 준비 중... (처음 한 번만 몇 초 걸림)"}
            </strong>
          )}

          {stage === "testing-mic" && (
            <>
              <div style={{ width: "100%", maxWidth: 320, height: 10, borderRadius: 6, background: "#eef0f6", overflow: "hidden" }}>
                <div
                  style={{
                    width: `${micLevel}%`,
                    height: "100%",
                    background: micLevel < 8 ? "#e05252" : "#596ff3",
                    transition: "width 60ms linear",
                  }}
                />
              </div>
              <span style={{ fontSize: 11, color: micLevel < 8 ? "#c0392b" : "#9098a7" }}>
                {micLevel < 8 ? "소리가 거의 안 잡혀요 - 마이크에 더 가까이서 말해보세요" : "마이크가 소리를 잡고 있어요"}
              </span>
              <div style={{ display: "flex", gap: 10 }}>
                <button className="primary-button" onClick={startRecording} type="button">
                  <Mic size={16} /> 답변 녹음 시작
                </button>
                <button className="text-button" onClick={cancelDeviceTest} type="button">
                  취소
                </button>
              </div>
            </>
          )}

          {stage === "get-ready" && <strong style={{ color: "#596ff3", fontSize: 16 }}>곧 녹음이 시작됩니다, 준비하세요...</strong>}

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
              <button className="primary-button" onClick={stopRecording} type="button">
                <Square size={14} /> 답변 완료
              </button>
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
            <button className="text-button" onClick={reset} type="button">
              다시 시도
            </button>
          )}
        </div>
      </section>

      {stage === "result" &&
        result &&
        (() => {
          const { count: fillerCount, parts: fillerParts } = analyzeFillers(result.transcript ?? "");
          const summary = buildSummarySentence(result, fillerCount);
          return (
            <section className="panel" style={{ marginTop: 20 }}>
              <div className="panel-title">
                <div>
                  <h2>분석 결과</h2>
                  <p>측정된 지표는 감정을 판단한 값이 아니라, 말투·표정의 객관적인 신호를 보여줍니다.</p>
                </div>
                <button className="text-button" onClick={reset} type="button">
                  <RotateCcw size={13} /> 다시 하기
                </button>
              </div>

              {summary && (
                <div style={{ marginBottom: 16, padding: "10px 14px", borderRadius: 10, background: "#f2f5ff", color: "#3a4a8f", fontSize: 13 }}>
                  {summary}
                </div>
              )}

              <div style={{ marginBottom: 18 }}>
                <span className="mini-label">인식된 답변</span>
                {result.low_confidence_transcript && (
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                      margin: "6px 0 0",
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
                <p style={{ margin: "6px 0 0", color: "#293349", fontSize: 13, lineHeight: 1.6 }}>
                  {result.transcript
                    ? fillerParts.map((part, i) =>
                        part.isFiller ? (
                          <mark key={i} style={{ background: "#ffe6a8", borderRadius: 3, padding: "0 2px" }}>
                            {part.text}
                          </mark>
                        ) : (
                          <span key={i}>{part.text}</span>
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

              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: 12 }}>
                {metricLabels.map(({ key, label, format, hint, noBaseline, gauge }) => {
                  const value = result.metrics[key];
                  const hasValue = value !== null && value !== undefined;
                  return (
                    <div key={key} style={{ border: "1px solid #e8ebf1", borderRadius: 10, padding: "10px 12px" }}>
                      <span className="mini-label">{label}</span>
                      <p style={{ margin: "4px 0 0", color: "#293349", fontSize: 15, fontWeight: 700 }}>{hasValue ? format(value) : "-"}</p>
                      {hasValue && gauge && <RangeGauge value={value} {...gauge} />}
                      {hasValue && hint && <p style={{ margin: "4px 0 0", fontSize: 11, color: "#6a7383" }}>{hint(value)}</p>}
                      {hasValue && !hint && noBaseline && <p style={{ margin: "4px 0 0", fontSize: 11, color: "#b0b6c0" }}>{NO_BASELINE_HINT}</p>}
                    </div>
                  );
                })}
                {result.transcript && (
                  <div style={{ border: "1px solid #e8ebf1", borderRadius: 10, padding: "10px 12px" }}>
                    <span className="mini-label">습관어 사용 횟수</span>
                    <p style={{ margin: "4px 0 0", color: "#293349", fontSize: 15, fontWeight: 700 }}>{fillerCount}회</p>
                  </div>
                )}
                {faceMetrics &&
                  faceMetricLabels.map(({ key, label, format, noBaseline }) => (
                    <div key={key} style={{ border: "1px solid #e8ebf1", borderRadius: 10, padding: "10px 12px" }}>
                      <span className="mini-label">{label}</span>
                      <p style={{ margin: "4px 0 0", color: "#293349", fontSize: 15, fontWeight: 700 }}>{format(faceMetrics[key] as number)}</p>
                      {noBaseline && <p style={{ margin: "4px 0 0", fontSize: 11, color: "#b0b6c0" }}>{NO_BASELINE_HINT}</p>}
                    </div>
                  ))}
              </div>

              {!faceMetrics && (
                <p className="analysis-muted" style={{ marginTop: 12, fontSize: 11 }}>
                  얼굴이 인식되지 않아 표정 관련 지표는 계산되지 않았습니다. 카메라 각도를 조정하고 다시 시도해 보세요.
                </p>
              )}

              {/* 2026-08-05: 질문+답변 내용+음성/얼굴 지표를 모아 Gemini가 종합 평가한 리포트.
                  위쪽 지표들은 이미 떠 있으니, 이 영역만 별도로 로딩 상태를 보여준다. */}
              <div style={{ marginTop: 18, borderTop: "1px solid #e8ebf1", paddingTop: 16 }}>
                <span className="mini-label">AI 종합 평가</span>
                {!report && !reportLoading && (
                  <div style={{ marginTop: 8 }}>
                    <button className="text-button" onClick={() => void loadReport()} type="button">
                      종합 평가 보기
                    </button>
                  </div>
                )}
                {reportLoading && (
                  <p style={{ display: "flex", alignItems: "center", gap: 8, margin: "8px 0 0", color: "#6a7383", fontSize: 13 }}>
                    <LoaderCircle className="spin" size={14} /> 평가를 생성하는 중입니다...
                  </p>
                )}
                {!reportLoading && report && (
                  <p style={{ margin: "8px 0 0", color: "#293349", fontSize: 13, lineHeight: 1.7 }}>{report}</p>
                )}
              </div>
            </section>
          );
        })()}
    </>
  );
}
