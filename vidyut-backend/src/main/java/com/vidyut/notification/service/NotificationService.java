package com.vidyut.notification.service;

import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.dto.NotificationPreferenceResponse;
import com.vidyut.notification.entity.NotificationType;

import java.util.List;

public interface NotificationService {
    void sendNotification(Long userId, String title, String message, NotificationType type);
    void sendNotification(Long userId, String title, String message, NotificationType type, String deepLink);
    List<Notification> getNotificationsForUser(Long userId);
    Notification markRead(Long userId, Long notificationId);
    void markAllRead(Long userId);
    long unreadCount(Long userId);
    List<NotificationPreferenceResponse> getPreferences(Long userId);
    NotificationPreferenceResponse updatePreference(Long userId, NotificationType type, boolean enabled);
    void registerDevice(Long userId, String token, String platform);
}
