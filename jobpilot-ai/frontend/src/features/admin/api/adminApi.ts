import { deleteJsonReturning, getJson, patchJson, putJson } from "../../../api/httpClient";

export type MemberRole = "USER" | "ADMIN";
export interface AdminOverview { memberCount: number; adminCount: number; jobPostingCount: number; activePostingCount: number; closedPostingCount: number; }
export interface AdminMember { id: number; loginId: string; email: string; nickname: string; onboardingCompleted: boolean; role: MemberRole; }
export interface AdminJobPosting { id: number; title: string; companyName: string | null; status: "ACTIVE" | "CLOSED" | "HIDDEN"; location: string | null; deadlineAt: string | null; viewCount: number; }
export interface AdminPage<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface UpdateAdminJobPostingRequest {
  title: string;
  companyName: string | null;
  location: string | null;
  deadlineAt: string | null;
  status: AdminJobPosting["status"];
}

const BASE = "/api/v1/admin";
export const getAdminOverview = () => getJson<AdminOverview>(`${BASE}/overview`);
export const getAdminMembers = (query = "") => getJson<AdminPage<AdminMember>>(`${BASE}/members?size=20&query=${encodeURIComponent(query)}`);
export const changeAdminMemberRole = (memberId: number, role: MemberRole) => patchJson<AdminMember>(`${BASE}/members/${memberId}/role`, { role });
export const getAdminJobPostings = (query = "", page = 0, size = 20, status = "ALL", sort = "deadline_asc") =>
  getJson<AdminPage<AdminJobPosting>>(`${BASE}/job-postings?size=${size}&page=${page}&query=${encodeURIComponent(query)}&status=${status}&sort=${sort}`);
export const changeAdminJobPostingStatus = (jobPostingId: number, status: AdminJobPosting["status"]) => patchJson<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}/status`, { status });
export const updateAdminJobPosting = (jobPostingId: number, request: UpdateAdminJobPostingRequest) =>
  putJson<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}`, request);
export const deleteAdminJobPosting = (jobPostingId: number) =>
  deleteJsonReturning<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}`);
