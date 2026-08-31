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
// title/body/url을 비워두면 서버가 기본 문구("테스트 알림")로 보낸다 - 실제 스케줄러 문구를
// 실기기에서 재현해보고 싶을 때만 채워 넣으면 된다.
export interface TestPushPayload {
  title?: string;
  body?: string;
  url?: string;
}

export function testSendPush(payload: TestPushPayload = {}): Promise<{ sent: boolean; reason?: string }> {
  return postJson<{ sent: boolean; reason?: string }>("/api/v1/push/test-send", payload);
}
