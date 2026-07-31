import { getJson } from "../../../api/httpClient";
import { jobMatchesFixture } from "../data/jobMatches.fixture";
import type { JobMatch, MatchGrade } from "../model/job.types";

export async function getJobMatches(grade?: MatchGrade | "ALL"): Promise<JobMatch[]> {
  const jobs = await getJson<JobMatch[]>("/api/v1/job-matches", jobMatchesFixture);
  return !grade || grade === "ALL" ? jobs : jobs.filter((job) => job.grade === grade);
}
