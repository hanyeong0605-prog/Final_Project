import { deleteJson, getJson, postForm, postJson, putJson } from "../../../api/httpClient";
import type { Project, ProjectInput, SelfIntroduction, SelfIntroductionInput } from "../model/resume.types";

const BASE = "/api/v1/members/me";

export const listSelfIntroductions = () => getJson<SelfIntroduction[]>(`${BASE}/self-introductions`);
export const createSelfIntroduction = (input: SelfIntroductionInput) =>
  postJson<SelfIntroduction>(`${BASE}/self-introductions`, input);
export const updateSelfIntroduction = (id: number, input: SelfIntroductionInput) =>
  putJson<SelfIntroduction>(`${BASE}/self-introductions/${id}`, input);
export const deleteSelfIntroduction = (id: number) => deleteJson(`${BASE}/self-introductions/${id}`);

export const listProjects = () => getJson<Project[]>(`${BASE}/projects`);
export const createProject = (input: ProjectInput) => postJson<Project>(`${BASE}/projects`, input);
export const updateProject = (id: number, input: ProjectInput) => putJson<Project>(`${BASE}/projects/${id}`, input);
export const deleteProject = (id: number) => deleteJson(`${BASE}/projects/${id}`);

export interface ResumeDocument { id: number; type: "UPLOADED" | "GENERATED"; title: string; originalFilename: string | null; templateKey: "STANDARD" | "PROJECT" | "COMPACT" | null; extractedText: string | null; generatedContent: string | null; extractedProfile: Record<string, unknown> | null; createdAt: string; }
export const listResumeDocuments = () => getJson<ResumeDocument[]>(`${BASE}/resume-documents`);
export const extractResumeDocument = (file: File) => { const data = new FormData(); data.append("file", file); return postForm<ResumeDocument>(`${BASE}/resume-documents/extract`, data); };
export const applyResumeExtraction = (id: number) => postJson<ResumeDocument>(`${BASE}/resume-documents/${id}/apply-profile`, undefined);
export const generateResumeDocument = (input: { title: string; additionalRequest: string; templateKey: string; answers?: string[]; enabledSections?: string[] }, templateFile?: File | null) => {
  const data = new FormData();
  data.append("request", new Blob([JSON.stringify(input)], { type: "application/json" }));
  if (templateFile) data.append("templateFile", templateFile);
  return postForm<ResumeDocument>(`${BASE}/resume-documents/generate`, data);
};
