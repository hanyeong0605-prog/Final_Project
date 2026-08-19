export type ResumeEntryType = "PERSONAL" | "EDUCATION" | "CAREER" | "ACTIVITY" | "TRAINING" | "AWARD" | "OVERSEAS" | "LANGUAGE" | "PORTFOLIO" | "PREFERENCE";

export interface ResumeEntry {
  id: number; entryType: ResumeEntryType; title: string; content: Record<string, string>; displayOrder: number; createdAt: string; updatedAt: string;
}
export interface ResumeEntryInput { entryType: ResumeEntryType; title: string; content: Record<string, string>; displayOrder: number; }
