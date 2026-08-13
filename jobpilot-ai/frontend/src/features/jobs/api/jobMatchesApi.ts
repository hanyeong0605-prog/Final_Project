import { getJson, postJson } from "../../../api/httpClient";
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
  requirementId: number | null;
  requirement: string | null;
  requirementType: string | null;
  sourceExcerpt: string | null;
  memberEvidenceType: string | null;
  status: RequirementStatus;
  comment: string | null;
  gapAction: string | null;
}

interface JobMatchDetailResponse {
  match: JobMatchSummaryResponse;
  evidences: JobMatchEvidenceResponse[];
  postingDescription: string | null;
}

function toJobMatch(value: JobMatchSummaryResponse): JobMatch {
  return {
    id: value.jobPostingId,
    company: value.companyName,
    title: value.title,
    source: "사람인",
    sourceUrl: value.sourceUrl,
    location: value.location ?? "근무지 미등록",
    deadline: value.deadlineAt ? new Intl.DateTimeFormat("ko-KR").format(new Date(value.deadlineAt)) : "상시채용",
    recommendationLevel: value.recommendationLevel,
    score: Number(value.readinessScore),
    comment: value.summaryComment ?? "분석 근거를 확인해 주세요.",
    postingDescription: "",
    skills: [],
    requirements: [],
  };
}

function toRequirement(value: JobMatchEvidenceResponse, index: number): RequirementEvidence {
  return {
    requirementId: value.requirementId ?? undefined,
    requirement: value.requirement ?? "공고 요구사항",
    requirementType: value.requirementType ?? "기타",
    sourceExcerpt: value.sourceExcerpt ?? value.requirement ?? "",
    sourceNumber: index + 1,
    memberEvidenceType: value.memberEvidenceType ?? undefined,
    status: value.status,
    evidence: value.comment ?? "등록한 스펙과의 연결 근거가 없습니다.",
    action: value.gapAction ?? (value.status === "MISSING" ? "관련 스펙을 등록하거나 준비 계획을 세워 보세요." : "공고 원문에서 해당 조건을 확인해 주세요."),
  };
}

export async function getJobMatches(level?: RecommendationLevel | "ALL"): Promise<JobMatch[]> {
  const response = await getJson<JobMatchSummaryResponse[]>("/api/v1/job-matches");
  const jobs = response.map(toJobMatch);
  return !level || level === "ALL" ? jobs : jobs.filter((job) => job.recommendationLevel === level);
}

export async function recalculateJobMatches(): Promise<number> {
  const response = await postJson<{ generated: number }>("/api/v1/job-matches/recalculate", undefined);
  return response.generated;
}

export async function getJobMatchDetail(jobPostingId: number): Promise<JobMatch> {
  const response = await getJson<JobMatchDetailResponse>(`/api/v1/job-matches/${jobPostingId}`);
  return {
    ...toJobMatch(response.match),
    postingDescription: response.postingDescription ?? "",
    requirements: response.evidences.map(toRequirement),
  };
}
