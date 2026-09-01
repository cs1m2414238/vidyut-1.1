import { Platform } from 'react-native';
import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { NotificationPreference, NotificationType, VidyutNotification } from './notification.types';

export async function getNotifications(): Promise<VidyutNotification[]> {
  try {
    const response = await apiClient.get<ApiResponse<VidyutNotification[]>>('/ev/notifications');
    return unwrapApiResponse(response.data).slice(0, 30);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load notifications.')); }
}

export async function getUnreadNotificationCount(): Promise<number> {
  try {
    const response = await apiClient.get<ApiResponse<number>>('/ev/notifications/unread-count');
    return unwrapApiResponse(response.data);
  } catch { return 0; }
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.patch(`/ev/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.patch('/ev/notifications/read-all');
}

export async function getNotificationPreferences(): Promise<NotificationPreference[]> {
  try {
    const response = await apiClient.get<ApiResponse<NotificationPreference[]>>('/ev/notifications/preferences');
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to load notification preferences.')); }
}

export async function updateNotificationPreference(type: NotificationType, enabled: boolean): Promise<NotificationPreference> {
  try {
    const response = await apiClient.put<ApiResponse<NotificationPreference>>('/ev/notifications/preferences', { type, enabled });
    return unwrapApiResponse(response.data);
  } catch (error) { throw new Error(getApiErrorMessage(error, 'Unable to update this preference.')); }
}

export async function registerPushDevice(token: string): Promise<void> {
  await apiClient.post('/ev/notifications/devices', { token, platform: Platform.OS });
}
