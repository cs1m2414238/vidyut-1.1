package com.vidyut.notification.service;

import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.entity.NotificationPreference;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.entity.PushDevice;
import com.vidyut.notification.dto.NotificationPreferenceResponse;
import com.vidyut.notification.repository.NotificationRepository;
import com.vidyut.notification.repository.NotificationPreferenceRepository;
import com.vidyut.notification.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final ExpoPushGateway pushGateway;

    @Override
    public void sendNotification(Long userId, String title, String message, NotificationType type) {
        sendNotification(userId, title, message, type, null);
    }

    @Override
    public void sendNotification(Long userId, String title, String message, NotificationType type, String deepLink) {
        if (!type.isSafetyCritical() && preferenceRepository.findByUserIdAndType(userId, type)
                .map(NotificationPreference::isEnabled).orElse(true) == false) return;
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .deepLink(deepLink)
                .critical(type.isSafetyCritical())
                .isRead(false)
                .build();
        pushGateway.deliver(notificationRepository.save(notification));
    }

    @Override
    public List<Notification> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    @Override
    public Notification markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Override
    public void markAllRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByTimestampDesc(userId);
        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public List<NotificationPreferenceResponse> getPreferences(Long userId) {
        Map<NotificationType, NotificationPreference> saved = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getType, Function.identity()));
        return Arrays.stream(NotificationType.values())
                .map(type -> new NotificationPreferenceResponse(type,
                        type.isSafetyCritical() || saved.getOrDefault(type,
                                NotificationPreference.builder().enabled(true).build()).isEnabled(),
                        type.isSafetyCritical()))
                .toList();
    }

    @Override
    public NotificationPreferenceResponse updatePreference(Long userId, NotificationType type, boolean enabled) {
        if (type.isSafetyCritical() && !enabled) {
            return new NotificationPreferenceResponse(type, true, true);
        }
        NotificationPreference preference = preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationPreference.builder().userId(userId).type(type).build());
        preference.setEnabled(enabled);
        preferenceRepository.save(preference);
        return new NotificationPreferenceResponse(type, enabled, false);
    }

    @Override
    public void registerDevice(Long userId, String token, String platform) {
        PushDevice device = pushDeviceRepository.findByToken(token).orElseGet(PushDevice::new);
        device.setUserId(userId);
        device.setToken(token.trim());
        device.setPlatform(platform.trim().toUpperCase());
        device.setEnabled(true);
        device.setUpdatedAt(LocalDateTime.now());
        pushDeviceRepository.save(device);
    }
}
