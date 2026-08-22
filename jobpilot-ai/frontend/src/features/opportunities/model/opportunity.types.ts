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
  address: string; phone: string; trainingTarget: string; capacity: number | null; enrolledCount: number | null; courseFee: number | null; selfPayFee: number | null; satisfactionScore: number | null; detailUrl: string; institutionUrl: string;
  startAt: string | null; ncsCode: string; contents: string; certificate: string; grade: string; employmentRate3m: string; employmentRate6m: string; thumbnailUrl: string;
}
