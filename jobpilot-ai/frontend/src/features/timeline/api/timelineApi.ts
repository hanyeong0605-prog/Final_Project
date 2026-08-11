import { getJson, postJson } from "../../../api/httpClient";
import type {
  InterviewSessionRecordDetail,
  InterviewSessionRecordInput,
  InterviewSessionRecordSummary,
} from "../model/timeline.types";

const BASE = "/api/v1/members/me/interview-sessions";

export const listInterviewSessions = () => getJson<InterviewSessionRecordSummary[]>(BASE);
export const getInterviewSessionDetail = (id: number) => getJson<InterviewSessionRecordDetail>(`${BASE}/${id}`);
export const saveInterviewSessionRecord = (input: InterviewSessionRecordInput) =>
  postJson<InterviewSessionRecordDetail>(BASE, input);
