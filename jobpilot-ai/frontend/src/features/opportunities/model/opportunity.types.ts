export type OpportunityType = "교육" | "자격증" | "공모전" | "청년지원";

export interface Opportunity {
  id: number;
  type: OpportunityType;
  title: string;
  organization: string;
  period: string;
  deadline: string;
  reason: string;
  tags: string[];
  sourceUrl: string;
  status: string;
}
