import { getEmployerJson, postEmployerJson } from "./employerHttpClient";
export interface EmployerNotificationItem { id: number; title: string; body: string; url: string; read: boolean; sentAt: string; }
export const listEmployerNotifications = () => getEmployerJson<EmployerNotificationItem[]>("/api/v1/employer/notifications");
export const getEmployerUnreadCount = () => getEmployerJson<{ count: number }>("/api/v1/employer/notifications/unread-count");
export const readEmployerNotification = (id: number) => postEmployerJson<void>(`/api/v1/employer/notifications/${id}/read`, {});
