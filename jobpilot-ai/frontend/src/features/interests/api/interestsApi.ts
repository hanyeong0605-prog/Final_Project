import { getJson, postJson } from "../../../api/httpClient";
import type { JobPosting } from "../../job-postings/model/jobPosting.types";

const targetType = "JOB_POSTING";

export function getInterestIds(): Promise<number[]> {
  return getJson<number[]>(`/api/v1/interests?targetType=${targetType}`);
}
export function getBookmarkedJobs(): Promise<JobPosting[]> {
  return getJson<JobPosting[]>("/api/v1/interests/job-postings");
}

export async function toggleInterest(targetId: number, interested: boolean): Promise<void> {
  await postJson("/api/v1/interests", { targetType, targetId, interested });
}
