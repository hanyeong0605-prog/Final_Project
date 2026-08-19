import { deleteJson, getJson, postJson, putJson } from "../../../api/httpClient";
import type { ResumeEntry, ResumeEntryInput } from "../model/resumeEntry.types";
const BASE = "/api/v1/members/me/resume-entries";
export const listResumeEntries = () => getJson<ResumeEntry[]>(BASE);
export const createResumeEntry = (input: ResumeEntryInput) => postJson<ResumeEntry>(BASE, input);
export const updateResumeEntry = (id: number, input: ResumeEntryInput) => putJson<ResumeEntry>(`${BASE}/${id}`, input);
export const deleteResumeEntry = (id: number) => deleteJson(`${BASE}/${id}`);
