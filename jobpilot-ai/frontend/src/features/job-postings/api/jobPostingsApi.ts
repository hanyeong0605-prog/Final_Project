import { getJson } from "../../../api/httpClient";
import type { JobPosting } from "../model/jobPosting.types";

export function getJobPostings(query = ""): Promise<JobPosting[]> {
  const parameter = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : "";
  return getJson<JobPosting[]>(`/api/v1/job-postings${parameter}`);
}
