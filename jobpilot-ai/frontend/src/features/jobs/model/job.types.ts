export type RecommendationLevel = "DIFFICULT_NOW" | "CHALLENGE_AFTER_GAPS" | "APPLY_NOW";
export type RequirementStatus = "DIRECT" | "RELATED" | "MISSING" | "CHECK_REQUIRED";

export interface RequirementEvidence {
  requirementId?: number;
  requirement: string;
  requirementType: string;
  sourceExcerpt: string;
  sourceNumber: number;
  memberEvidenceType?: string;
  memberEvidence?: string;
  evidence: string;
  status: RequirementStatus;
  action: string;
}

export interface JobMatch {
  id: number;
  company: string;
  title: string;
  source: "사람인";
  sourceUrl: string;
  location: string;
  deadline: string;
  recommendationLevel: RecommendationLevel;
  score: number;
  comment: string;
  postingDescription: string;
  skills: string[];
  requirements: RequirementEvidence[];
}

export interface GrowthResourceRecommendation { type: "CERTIFICATE" | "TRAINING" | "BOOK"; label: string; title: string; description: string; href: string; }
export interface GrowthAction { requirementId: number | null; requirement: string; category: string; title: string; description: string; nextStep: string; href: string; relatedRequirementIds?: number[]; recommendations?: GrowthResourceRecommendation[]; }
