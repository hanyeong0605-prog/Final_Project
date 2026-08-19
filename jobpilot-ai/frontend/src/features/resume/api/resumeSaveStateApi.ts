import { getJson, putJson } from "../../../api/httpClient";

export type ResumeSaveState = { status: "NOT_SAVED" | "DRAFT" | "SAVED"; updatedAt: string | null };
const BASE = "/api/v1/members/me/resume-save-state";
export const getResumeSaveState = () => getJson<ResumeSaveState>(BASE);
export const saveResumeSaveState = (status: "DRAFT" | "SAVED") => putJson<ResumeSaveState>(BASE, { status });
