export type RecommendationLevel = "DIFFICULT_NOW" | "CHALLENGE_AFTER_GAPS" | "APPLY_NOW";
export type RequirementStatus = "DIRECT" | "RELATED" | "MISSING" | "CHECK_REQUIRED";

export interface RequirementEvidence {
  requirement: string;
  requirementType: string;
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
  skills: string[];
  requirements: RequirementEvidence[];
}
