import { getJson } from "../../../api/httpClient";
import type { JobMatch, RecommendationLevel } from "../model/job.types";

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

export async function getJobMatches(level?: RecommendationLevel | "ALL"): Promise<JobMatch[]> {
  const response = await getJson<JobMatchSummaryResponse[]>("/api/v1/job-matches");
  const jobs = response.map(toJobMatch);
  return !level || level === "ALL" ? jobs : jobs.filter((job) => job.recommendationLevel === level);
}
