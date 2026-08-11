import { getJson, patchJson } from "../../../api/httpClient";

export type MemberRole = "USER" | "ADMIN";
export interface AdminOverview { memberCount: number; adminCount: number; jobPostingCount: number; activePostingCount: number; closedPostingCount: number; }
export interface AdminMember { id: number; loginId: string; email: string; nickname: string; onboardingCompleted: boolean; role: MemberRole; }
export interface AdminJobPosting { id: number; title: string; companyName: string | null; status: "ACTIVE" | "CLOSED" | "HIDDEN"; location: string | null; deadlineAt: string | null; viewCount: number; }
export interface AdminPage<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

const BASE = "/api/v1/admin";
export const getAdminOverview = () => getJson<AdminOverview>(`${BASE}/overview`);
export const getAdminMembers = (query = "") => getJson<AdminPage<AdminMember>>(`${BASE}/members?size=20&query=${encodeURIComponent(query)}`);
export const changeAdminMemberRole = (memberId: number, role: MemberRole) => patchJson<AdminMember>(`${BASE}/members/${memberId}/role`, { role });
export const getAdminJobPostings = (query = "") => getJson<AdminPage<AdminJobPosting>>(`${BASE}/job-postings?size=20&query=${encodeURIComponent(query)}`);
export const changeAdminJobPostingStatus = (jobPostingId: number, status: AdminJobPosting["status"]) => patchJson<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}/status`, { status });
