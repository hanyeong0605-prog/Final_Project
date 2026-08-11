import { getJson } from "../../../api/httpClient";
import type { JobMatch, RecommendationLevel, RequirementEvidence, RequirementStatus } from "../model/job.types";

interface JobMatchSummaryResponse {
  jobPostingId: number;
  companyName: string;
  title: string;
  sourceUrl: string;
  location: string | null;
  deadlineAt: string | null;
  recommendationLevel: RecommendationLevel;
  readinessScore: number;
  summaryComment: string | null;
}

interface JobMatchEvidenceResponse {
  requirement: string | null;
  requirementType: string | null;
  status: RequirementStatus;
  comment: string | null;
  gapAction: string | null;
}

interface JobMatchDetailResponse {
  match: JobMatchSummaryResponse;
  evidences: JobMatchEvidenceResponse[];
}

function toJobMatch(value: JobMatchSummaryResponse): JobMatch {
  return {
    id: value.jobPostingId,
    company: value.companyName,
    title: value.title,
    source: "사람인",
    sourceUrl: value.sourceUrl,
    location: value.location ?? "근무지역 미등록",
    deadline: value.deadlineAt ? new Intl.DateTimeFormat("ko-KR").format(new Date(value.deadlineAt)) : "상시채용",
    recommendationLevel: value.recommendationLevel,
    score: Number(value.readinessScore),
    comment: value.summaryComment ?? "분석 의견이 없습니다.",
    skills: [],
    requirements: [],
  };
}

function toRequirement(value: JobMatchEvidenceResponse): RequirementEvidence {
  return {
    requirement: value.requirement ?? "공고 요구사항",
    requirementType: value.requirementType ?? "필수",
    status: value.status,
    evidence: value.comment ?? "등록된 내 스펙과의 연결 근거가 없습니다.",
    action: value.gapAction ?? (value.status === "MISSING" ? "관련 스펙을 등록하거나 준비 계획을 세워 보세요." : "현재 스펙에서 확인된 항목입니다."),
  };
}

export async function getJobMatches(level?: RecommendationLevel | "ALL"): Promise<JobMatch[]> {
  const response = await getJson<JobMatchSummaryResponse[]>("/api/v1/job-matches");
  const jobs = response.map(toJobMatch);
  return !level || level === "ALL" ? jobs : jobs.filter((job) => job.recommendationLevel === level);
}

export async function getJobMatchDetail(jobPostingId: number): Promise<JobMatch> {
  const response = await getJson<JobMatchDetailResponse>(`/api/v1/job-matches/${jobPostingId}`);
  return { ...toJobMatch(response.match), requirements: response.evidences.map(toRequirement) };
}
