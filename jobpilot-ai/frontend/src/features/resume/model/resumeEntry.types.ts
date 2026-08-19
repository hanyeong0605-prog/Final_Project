export type ResumeEntryType = "EDUCATION" | "CAREER" | "ACTIVITY" | "AWARD" | "LANGUAGE" | "PORTFOLIO";

export interface ResumeEntry {
  id: number; entryType: ResumeEntryType; title: string; content: Record<string, string>; displayOrder: number; createdAt: string; updatedAt: string;
}
export interface ResumeEntryInput { entryType: ResumeEntryType; title: string; content: Record<string, string>; displayOrder: number; }
