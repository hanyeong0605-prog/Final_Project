import { getJson, postJson } from "../../../api/httpClient";

export type AdminFacePairing = { sessionId: string; token: string; expiresAt: string };
export type AdminFacePairingStatus = "WAITING" | "VERIFIED" | "REJECTED";
export type AdminFacePairingResult = { status: AdminFacePairingStatus; similarity: number | null; message: string | null; expiresAt: string | null };

const BASE = "/api/v1/admin/face-pairings";
export const createAdminFacePairing = () => postJson<AdminFacePairing>(BASE, {});
export const getAdminFacePairingResult = (sessionId: string) => getJson<AdminFacePairingResult>(`${BASE}/${encodeURIComponent(sessionId)}`);
export const submitAdminFaceCapture = (sessionId: string, token: string, imageBase64: string) =>
  postJson<AdminFacePairingResult>(`${BASE}/${encodeURIComponent(sessionId)}/capture`, { token, imageBase64 });
