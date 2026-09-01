import { apiRequest } from './api';

interface NotificationPayload {
  id: number;
  userId: number;
  title: string;
  message: string;
  type: string;
  read?: boolean;
  isRead?: boolean;
  timestamp: string;
  deepLink?: string;
  critical?: boolean;
}

export interface EvNotification {
  id: number;
  title: string;
  message: string;
  type: string;
  read: boolean;
  timestamp: string;
  deepLink?: string;
  critical: boolean;
}

export interface EvNotificationPreference { type: string; enabled: boolean; critical: boolean }

export async function getEvNotifications(token: string): Promise<EvNotification[]> {
  const notifications = await apiRequest<NotificationPayload[]>('/ev/notifications', {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
  });

  return notifications.map((notification) => ({
    id: notification.id,
    title: notification.title,
    message: notification.message,
    type: notification.type,
    read: Boolean(notification.read ?? notification.isRead),
    timestamp: notification.timestamp,
    deepLink: notification.deepLink,
    critical: Boolean(notification.critical),
  }));
}

export function markEvNotificationRead(token: string, id: number) {
  return apiRequest(`/ev/notifications/${id}/read`, { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } });
}

export function markAllEvNotificationsRead(token: string) {
  return apiRequest('/ev/notifications/read-all', { method: 'PATCH', headers: { Authorization: `Bearer ${token}` } });
}

export function getEvNotificationPreferences(token: string): Promise<EvNotificationPreference[]> {
  return apiRequest('/ev/notifications/preferences', { method: 'GET', headers: { Authorization: `Bearer ${token}` } });
}

export function updateEvNotificationPreference(token: string, type: string, enabled: boolean): Promise<EvNotificationPreference> {
  return apiRequest('/ev/notifications/preferences', { method: 'PUT', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify({ type, enabled }) });
}
