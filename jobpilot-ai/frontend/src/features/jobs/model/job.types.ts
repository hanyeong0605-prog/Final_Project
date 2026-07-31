export type MatchGrade = "READY_TO_APPLY" | "NEEDS_IMPROVEMENT" | "INSUFFICIENT_EVIDENCE";
export type RequirementStatus = "DIRECT" | "RELATED" | "MISSING" | "CHECK_REQUIRED";

export interface RequirementEvidence {
  requirement: string;
  requirementType: "필수" | "우대";
  evidence: string;
  status: RequirementStatus;
  action: string;
}

export interface JobMatch {
  id: number;
  company: string;
  title: string;
  source: "고용24" | "잡코리아" | "직접 등록";
  location: string;
  deadline: string;
  grade: MatchGrade;
  score: number;
  comment: string;
  skills: string[];
  requirements: RequirementEvidence[];
}
