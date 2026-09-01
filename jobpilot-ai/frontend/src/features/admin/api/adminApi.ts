import { deleteJson, deleteJsonReturning, getJson, patchJson, postJson, putJson } from "../../../api/httpClient";

export type MemberRole = "USER" | "ADMIN";
export type EmployerAccountStatus = "PENDING" | "APPROVED" | "REJECTED";
export interface AdminOverview {
  memberCount: number; adminCount: number; jobPostingCount: number; activePostingCount: number; closedPostingCount: number;
  todayVisitorCount: number; todayUserVisitorCount: number; todayAdminVisitorCount: number; employerPendingCount: number;
}
export interface AdminEmployer {
  id: number; companyName: string; managerName: string; managerPhone: string | null; email: string;
  businessRegistrationNumber: string; representativeName: string; openingDate: string;
  ntsVerified: boolean; status: EmployerAccountStatus; rejectionReason: string | null; createdAt: string;
}
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
export interface AdminHomePromotion { id: number; slotType: "TRAINING" | "BOOK"; title: string; provider: string | null; description: string | null; imageUrl: string | null; targetUrl: string; }
export interface AdminTrainingPromotionCandidate { id: number; title: string; organization: string | null; period: string; thumbnailUrl: string | null; targetUrl: string; }
export interface CreateAdminHomePromotion { slotType: "TRAINING" | "BOOK"; sourceKey: string; title: string; provider?: string | null; description?: string | null; imageUrl?: string | null; targetUrl: string; }

const BASE = "/api/v1/admin";
export const getAdminOverview = () => getJson<AdminOverview>(`${BASE}/overview`);
export const getAdminMembers = (query = "") => getJson<AdminPage<AdminMember>>(`${BASE}/members?size=20&query=${encodeURIComponent(query)}`);
export const changeAdminMemberRole = (memberId: number, role: MemberRole) => patchJson<AdminMember>(`${BASE}/members/${memberId}/role`, { role });
export const changeAdminMemberRoles = (memberIds: number[], role: MemberRole) =>
  patchJson<{ updatedCount: number }>(`${BASE}/members/bulk-role`, { memberIds, role });
export const deleteAdminMember = (memberId: number) => deleteJsonReturning<AdminMember>(`${BASE}/members/${memberId}`);
export const getAdminJobPostings = (query = "", page = 0, size = 20, status = "ALL", sort = "deadline_asc") =>
  getJson<AdminPage<AdminJobPosting>>(`${BASE}/job-postings?size=${size}&page=${page}&query=${encodeURIComponent(query)}&status=${status}&sort=${sort}`);
export const changeAdminJobPostingStatus = (jobPostingId: number, status: AdminJobPosting["status"]) => patchJson<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}/status`, { status });
export const changeAdminJobPostingStatuses = (jobPostingIds: number[], status: AdminJobPosting["status"]) =>
  patchJson<{ updatedCount: number }>(`${BASE}/job-postings/bulk-status`, { jobPostingIds, status });
export const updateAdminJobPosting = (jobPostingId: number, request: UpdateAdminJobPostingRequest) =>
  putJson<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}`, request);
export const deleteAdminJobPosting = (jobPostingId: number) =>
  deleteJsonReturning<AdminJobPosting>(`${BASE}/job-postings/${jobPostingId}`);
export const getAdminHomePromotions = () => getJson<AdminHomePromotion[]>(`${BASE}/home-promotions`);
export const getAdminTrainingPromotionCandidates = (query = "") => getJson<AdminPage<AdminTrainingPromotionCandidate>>(`${BASE}/home-promotions/trainings?size=10&query=${encodeURIComponent(query)}`);
export const createAdminHomePromotion = (request: CreateAdminHomePromotion) => postJson<AdminHomePromotion>(`${BASE}/home-promotions`, request);
export const deleteAdminHomePromotion = (promotionId: number) => deleteJson<void>(`${BASE}/home-promotions/${promotionId}`);
export const getAdminEmployers = (query = "", status: EmployerAccountStatus | "ALL" = "ALL", page = 0, size = 20) =>
  getJson<AdminPage<AdminEmployer>>(`${BASE}/employers?size=${size}&page=${page}&query=${encodeURIComponent(query)}&status=${status}`);
export const approveAdminEmployer = (employerId: number) => patchJson<AdminEmployer>(`${BASE}/employers/${employerId}/approve`, {});
export const rejectAdminEmployer = (employerId: number, reason: string) =>
  patchJson<AdminEmployer>(`${BASE}/employers/${employerId}/reject`, { reason });
