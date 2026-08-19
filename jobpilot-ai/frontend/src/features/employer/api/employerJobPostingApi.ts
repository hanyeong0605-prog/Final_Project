import { deleteEmployerJson, getEmployerJson, postEmployerJson, putEmployerJson } from "./employerHttpClient";

export interface EmployerJobPostingInput {
  title: string;
  companyUrl?: string;
  description: string;
  location?: string;
  employmentType?: string;
  experienceType?: string;
  salary?: string;
  deadlineAt?: string | null;
  rollingDeadline: boolean;
}

export interface EmployerJobPosting extends EmployerJobPostingInput {
  id: number;
  companyName: string;
  status: "ACTIVE" | "CLOSED" | "HIDDEN";
  publishedAt: string | null;
  viewCount: number;
}

export interface EmployerJobPostingPage {
  content: EmployerJobPosting[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function getMyJobPostings(page = 0, size = 20) {
  return getEmployerJson<EmployerJobPostingPage>(`/api/v1/employer/job-postings?page=${page}&size=${size}`);
}

export function createJobPosting(input: EmployerJobPostingInput) {
  return postEmployerJson<EmployerJobPosting>("/api/v1/employer/job-postings", input);
}

export function updateJobPosting(id: number, input: EmployerJobPostingInput) {
  return putEmployerJson<EmployerJobPosting>(`/api/v1/employer/job-postings/${id}`, input);
}

export function hideJobPosting(id: number) {
  return deleteEmployerJson<EmployerJobPosting>(`/api/v1/employer/job-postings/${id}`);
}
