import type { MatchGrade, RequirementStatus } from "./job.types";

export const gradeMeta: Record<MatchGrade, { label: string; tone: string }> = {
  READY_TO_APPLY: { label: "지원 조건 충족 가능", tone: "ready" },
  NEEDS_IMPROVEMENT: { label: "보완 후 도전 가능", tone: "improve" },
  INSUFFICIENT_EVIDENCE: { label: "현재 근거 부족", tone: "insufficient" },
};

export const evidenceMeta: Record<RequirementStatus, { label: string; tone: string }> = {
  DIRECT: { label: "직접 증명", tone: "success" },
  RELATED: { label: "관련 경험", tone: "info" },
  MISSING: { label: "보완 필요", tone: "warning" },
  CHECK_REQUIRED: { label: "확인 필요", tone: "neutral" },
};
