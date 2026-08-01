import type { RecommendationLevel, RequirementStatus } from "./job.types";

export const gradeMeta: Record<RecommendationLevel, { label: string; tone: string }> = {
  APPLY_NOW: { label: "지금도 지원해볼 만함", tone: "ready" },
  CHALLENGE_AFTER_GAPS: { label: "요건 보완 후 도전 가능", tone: "improve" },
  DIFFICULT_NOW: { label: "현재는 지원이 어려움", tone: "insufficient" },
};

export const evidenceMeta: Record<RequirementStatus, { label: string; tone: string }> = {
  DIRECT: { label: "직접 증명", tone: "success" },
  RELATED: { label: "관련 경험", tone: "info" },
  MISSING: { label: "보완 필요", tone: "warning" },
  CHECK_REQUIRED: { label: "확인 필요", tone: "neutral" },
};
