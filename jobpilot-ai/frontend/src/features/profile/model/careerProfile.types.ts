export interface CareerProfile {
  targetRole: string; targetJobFamily: string; preferredLocations: string[]; availableFrom: string | null;
  experienceType: string; githubUsername: string | null; educationLevel: string | null; schoolName: string | null;
  major: string | null; graduationStatus: string | null; totalCareerMonths: number;
  technicalSummary: string | null; portfolioUrl: string | null; profilePhotoDataUrl: string | null;
}
export const emptyCareerProfile = (): CareerProfile => ({ targetRole: "", targetJobFamily: "IT개발·데이터", preferredLocations: [], availableFrom: null, experienceType: "ENTRY", githubUsername: null, educationLevel: null, schoolName: null, major: null, graduationStatus: null, totalCareerMonths: 0, technicalSummary: null, portfolioUrl: null, profilePhotoDataUrl: null });
