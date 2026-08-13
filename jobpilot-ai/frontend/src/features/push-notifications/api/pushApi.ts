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

// 관리자 전용 - 스케줄러(08:30/09:00)를 기다리지 않고 본인 계정으로 테스트 알림 1건을 바로 보낸다.
export function testSendPush(): Promise<{ sent: boolean; reason?: string }> {
  return postJson<{ sent: boolean; reason?: string }>("/api/v1/push/test-send", {});
}
