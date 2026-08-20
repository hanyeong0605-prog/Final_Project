import { deleteJson, getAccessToken, getJson, postForm, postJson, putJson } from "../../../api/httpClient";
import type { Project, ProjectInput, SelfIntroduction, SelfIntroductionInput } from "../model/resume.types";

const BASE = "/api/v1/members/me";
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

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
export interface ResumeDraftContext { profile: string[]; skills: string[]; certificates: string[]; education: string[]; projects: string[]; }
export const listResumeDocuments = () => getJson<ResumeDocument[]>(`${BASE}/resume-documents`);
export const getResumeDraftContext = () => getJson<ResumeDraftContext>(`${BASE}/resume-documents/draft-context`);
export const extractResumeDocument = (file: File) => { const data = new FormData(); data.append("file", file); return postForm<ResumeDocument>(`${BASE}/resume-documents/extract`, data); };
export const applyResumeExtraction = (id: number) => postJson<ResumeDocument>(`${BASE}/resume-documents/${id}/apply-profile`, undefined);
export const deleteResumeDocument = (id: number) => deleteJson(`${BASE}/resume-documents/${id}`);
export const generateResumeDocument = (input: { title: string; additionalRequest: string; templateKey: string; answers?: string[]; enabledSections?: string[] }, templateFile?: File | null) => {
  const data = new FormData();
  data.append("request", new Blob([JSON.stringify(input)], { type: "application/json" }));
  if (templateFile) data.append("templateFile", templateFile);
  return postForm<ResumeDocument>(`${BASE}/resume-documents/generate`, data);
};

// 다운로드 요청도 앱의 인증 토큰이 필요하다. 일반 <a href>는 Authorization 헤더를
// 붙일 수 없어 보안 필터가 홈 화면으로 돌려보내므로 Blob으로 받아 내려받기를 시작한다.
export async function downloadResumeDocument(id: number): Promise<void> {
  const token = getAccessToken();
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${apiBaseUrl}${BASE}/resume-documents/${id}/download.docx`, { headers });
  if (!response.ok) throw new Error(`Word 파일을 내려받지 못했습니다. (HTTP ${response.status})`);
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : `resume-${id}.docx`;
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
  URL.revokeObjectURL(url);
}
