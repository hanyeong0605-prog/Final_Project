import { getEmployerJson, postEmployerJson } from "./employerHttpClient";
export type EmployerTalent = { memberId: number; nickname: string; targetRole: string; targetJobFamily: string; preferredLocations: string; experienceType: string; totalCareerMonths: number; technicalSummary?: string | null; portfolioUrl?: string | null; skills: string[] };
export const getEmployerTalents = (query = "") => getEmployerJson<EmployerTalent[]>(`/api/v1/employer/talents?query=${encodeURIComponent(query)}`);
export const getEmployerTalent = (memberId: number) => getEmployerJson<EmployerTalent>(`/api/v1/employer/talents/${memberId}`);
export const getEmployerTalentFavorites = () => getEmployerJson<EmployerTalent[]>("/api/v1/employer/talents/favorites");
export const toggleEmployerTalentFavorite = (memberId: number) => postEmployerJson<{ favorite: boolean }>(`/api/v1/employer/talents/${memberId}/favorite`, {});
