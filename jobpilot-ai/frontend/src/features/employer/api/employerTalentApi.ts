import { getEmployerJson, postEmployerJson } from "./employerHttpClient";
export type EmployerTalentEntry = { type: string; title: string; detail: string };
export type EmployerTalent = { memberId: number; nickname: string; targetRole: string; targetJobFamily: string; preferredLocations: string; experienceType: string; totalCareerMonths: number; technicalSummary?: string | null; portfolioUrl?: string | null; profilePhotoDataUrl?: string | null; skills: string[]; entries: EmployerTalentEntry[]; certificates: { name: string; issuer?: string | null; acquiredAt?: string | null }[]; selfIntroductions: { title: string; content: string; primary: boolean }[] };
export const getEmployerTalents = (query = "") => getEmployerJson<EmployerTalent[]>(`/api/v1/employer/talents?query=${encodeURIComponent(query)}`);
export const getEmployerTalent = (memberId: number) => getEmployerJson<EmployerTalent>(`/api/v1/employer/talents/${memberId}`);
export const getEmployerTalentFavorites = () => getEmployerJson<EmployerTalent[]>("/api/v1/employer/talents/favorites");
export const toggleEmployerTalentFavorite = (memberId: number) => postEmployerJson<{ favorite: boolean }>(`/api/v1/employer/talents/${memberId}/favorite`, {});
