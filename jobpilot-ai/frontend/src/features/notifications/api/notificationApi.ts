import { getJson, postJson } from "../../../api/httpClient";

export interface NotificationItem {
  id: number;
  title: string | null;
  body: string | null;
  url: string | null;
  read: boolean;
  sentAt: string;
}

// 2026-08-13: 상단바 종 아이콘 드롭다운. 마감임박/추천공고/테스트 알림 등 이미 발송된
// 웹푸시 이력(NotificationLog)을 그대로 보여준다 - 별도 "인앱 알림"을 새로 만들지 않았다.
export function listNotifications(): Promise<NotificationItem[]> {
  return getJson<NotificationItem[]>("/api/v1/notifications");
}

export function getUnreadCount(): Promise<{ count: number }> {
  return getJson<{ count: number }>("/api/v1/notifications/unread-count");
}

export function markNotificationRead(id: number): Promise<void> {
  return postJson("/api/v1/notifications/" + id + "/read", {});
}

export function markAllNotificationsRead(): Promise<void> {
  return postJson("/api/v1/notifications/read-all", {});
}
