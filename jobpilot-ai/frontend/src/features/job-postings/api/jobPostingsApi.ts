import { getJson } from "../../../api/httpClient";
import type { JobPostingDetail, JobPostingPage, JobPostingSearchParams } from "../model/jobPosting.types";

export function getJobPostings(params: JobPostingSearchParams = {}): Promise<JobPostingPage> {
  const query = new URLSearchParams();
  if (params.query?.trim()) query.set("query", params.query.trim());
  if (params.roles && params.roles.length > 0) query.set("roles", params.roles.join(","));
  if (params.experience) query.set("experience", params.experience);
  if (params.location) query.set("location", params.location);
  if (params.employmentType) query.set("employmentType", params.employmentType);
  query.set("sort", params.sort ?? "deadline_asc");
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 24));
  return getJson<JobPostingPage>(`/api/v1/job-postings?${query.toString()}`);
}

export function getJobPosting(id: string, init: RequestInit = {}): Promise<JobPostingDetail> {
  return getJson<JobPostingDetail>(`/api/v1/job-postings/${encodeURIComponent(id)}`, init);
}
