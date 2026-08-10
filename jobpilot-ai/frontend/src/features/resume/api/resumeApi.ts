import { deleteJson, getJson, postJson, putJson } from "../../../api/httpClient";
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
