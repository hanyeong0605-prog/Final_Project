import { getJson, postJson } from "../../../api/httpClient";

export interface PushSubscribeRequest {
  endpoint: string;
  p256dh: string;
  auth: string;
}

export function getVapidPublicKey(): Promise<{ publicKey: string }> {
  return getJson<{ publicKey: string }>("/api/v1/push/vapid-public-key");
}

export function subscribePush(subscription: PushSubscribeRequest): Promise<void> {
  return postJson("/api/v1/push/subscribe", subscription);
}

export function unsubscribePush(endpoint: string): Promise<void> {
  return postJson("/api/v1/push/unsubscribe", { endpoint });
}
