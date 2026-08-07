import { postJson } from "../../../api/httpClient";

export type CameraPairing = {
  roomId: string;
  pairingToken: string;
  socketTicket: string;
  expiresAt: string;
};

export type JoinedCameraPairing = {
  roomId: string;
  socketTicket: string;
  expiresAt: string;
};

export const createCameraPairing = () => postJson<CameraPairing>("/api/v1/camera-pairings", {});
export const joinCameraPairing = (roomId: string, pairingToken: string) =>
  postJson<JoinedCameraPairing>("/api/v1/camera-pairings/join", { roomId, pairingToken });
