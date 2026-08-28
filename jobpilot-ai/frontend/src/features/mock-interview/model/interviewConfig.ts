export type InterviewKind = "practice" | "real";
export type InterviewType = "종합" | "인성" | "역량" | "직무";
export type InterviewQuestionSource = "spec" | "spec_company" | "company";
export type InterviewCategory =
  | "가치관_자기관리"
  | "협업_리더십_커뮤니케이션"
  | "문제해결_도전경험"
  | "강점_약점"
  | "기술_직무역량";

export type RealInterviewSlot = {
  kind: "intro" | "rag" | "behavioral" | "closing";
  category?: InterviewCategory;
  angle?: string;
};

const PRACTICE_CATEGORIES: Record<InterviewType, InterviewCategory[]> = {
  종합: ["협업_리더십_커뮤니케이션", "문제해결_도전경험", "기술_직무역량", "가치관_자기관리"],
  인성: ["가치관_자기관리", "협업_리더십_커뮤니케이션"],
  역량: ["문제해결_도전경험", "강점_약점"],
  직무: ["기술_직무역량"],
};

const RAG_ANGLES = [
  "대표 프로젝트에서 맡은 역할과 실제 기여도",
  "기술 선택 이유와 대안 비교",
  "문제 상황의 원인 분석과 해결 과정",
  "성과를 수치나 결과로 검증",
  "지원 직무 요구사항에 경험을 적용",
  "부족한 역량을 보완할 계획",
];

/** 모드별 질문 수 범위. UI(min/max 속성, 안내 문구, 버튼 비활성화)도 이 값을 그대로 쓴다 -
 *  범위가 두 군데 적혀 있으면 한쪽만 고쳐서 입력창과 검증이 어긋나기 쉽다. */
export function questionCountRange(kind: InterviewKind): [number, number] {
  return kind === "practice" ? [2, 5] : [5, 10];
}

export function clampQuestionCount(kind: InterviewKind, value: number): number {
  const [min, max] = questionCountRange(kind);
  return Math.min(max, Math.max(min, Number.isFinite(value) ? Math.round(value) : min));
}

/** 입력창에 직접 타이핑한 문자열을 확정값으로 바꾼다(포커스를 벗어날 때 호출).
 *
 *  비어 있거나 숫자가 아니면 그 모드의 최솟값으로 되돌린다 - 입력 중에는 빈 문자열을 그대로
 *  두는 게 자연스럽지만(지우고 다시 치는 흐름), 확정 시점에까지 빈 값이나 NaN이 남으면
 *  질문 수가 0이 되거나 계산이 통째로 NaN으로 번진다. */
export function normalizeQuestionCountInput(kind: InterviewKind, raw: string): number {
  const trimmed = raw.trim();
  if (!trimmed) return questionCountRange(kind)[0];
  return clampQuestionCount(kind, Number(trimmed));
}

/** −/+ 버튼용. 범위 밖으로 나가지 않는다. */
export function stepQuestionCount(kind: InterviewKind, current: number, delta: number): number {
  return clampQuestionCount(kind, current + delta);
}

export function buildPracticeCategories(type: InterviewType, count: number): InterviewCategory[] {
  const pool = PRACTICE_CATEGORIES[type];
  return Array.from({ length: Math.max(0, count) }, (_, index) => pool[index % pool.length]);
}

export function buildRealInterviewSlots(questionCount: number): RealInterviewSlot[] {
  const count = clampQuestionCount("real", questionCount);
  const behavioralCount = count >= 8 ? 2 : 1;
  const middleCount = count - 2;
  const ragCount = middleCount - behavioralCount;
  const slots: RealInterviewSlot[] = [{ kind: "intro" }];
  for (let index = 0; index < ragCount; index += 1) {
    slots.push({ kind: "rag", category: "기술_직무역량", angle: RAG_ANGLES[index % RAG_ANGLES.length] });
  }
  for (let index = 0; index < behavioralCount; index += 1) {
    slots.push({
      kind: "behavioral",
      category: index % 2 === 0 ? "협업_리더십_커뮤니케이션" : "문제해결_도전경험",
      angle: index % 2 === 0 ? "협업 중 의견 충돌을 조율한 실제 행동" : "예상하지 못한 문제를 해결한 과정",
    });
  }
  slots.push({ kind: "closing" });
  return slots;
}
