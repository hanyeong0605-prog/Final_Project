import { getJson, putJson } from "../../../api/httpClient";
export interface ResumeAiConsent { agreed: boolean; }
const BASE = "/api/v1/members/me/resume-ai-consent";
export const getResumeAiConsent = () => getJson<ResumeAiConsent>(BASE);
export const saveResumeAiConsent = (agreed: boolean) => putJson<ResumeAiConsent>(BASE, { agreed });
