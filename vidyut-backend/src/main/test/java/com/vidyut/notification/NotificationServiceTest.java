package com.vidyut.notification;

import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NotificationServiceTest {
    @Autowired NotificationService notificationService;

    @Test
    void companyInboxCanMarkOneOrAllNotificationsRead() {
        Long companyAccountId = 87001L;
        notificationService.sendNotification(companyAccountId, "Compliance review", "Product evidence received", NotificationType.SYSTEM_ALERT);
        notificationService.sendNotification(companyAccountId, "Network alert", "A supplied charger needs attention", NotificationType.SYSTEM_ALERT);

        var initial = notificationService.getNotificationsForUser(companyAccountId);
        assertThat(initial).hasSize(2).allMatch(notification -> !notification.isRead());

        notificationService.markRead(companyAccountId, initial.get(0).getId());
        assertThat(notificationService.getNotificationsForUser(companyAccountId))
                .filteredOn(notification -> notification.isRead()).hasSize(1);

        notificationService.markAllRead(companyAccountId);
        assertThat(notificationService.getNotificationsForUser(companyAccountId))
                .allMatch(notification -> notification.isRead());
    }

    @Test
    void preferencesSuppressOptionalAlertsButNeverSafetyAlerts() {
        Long userId = 87002L;
        notificationService.updatePreference(userId, NotificationType.BOOKING_REMINDER, false);
        var criticalPreference = notificationService.updatePreference(
                userId, NotificationType.FAULT_ALERT, false);

        notificationService.sendNotification(userId, "Reminder", "Optional reminder",
                NotificationType.BOOKING_REMINDER);
        notificationService.sendNotification(userId, "Fault", "Safety action required",
                NotificationType.FAULT_ALERT, "vidyut://session/1");

        assertThat(criticalPreference.enabled()).isTrue();
        assertThat(notificationService.getNotificationsForUser(userId)).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.FAULT_ALERT);
            assertThat(notification.isCritical()).isTrue();
            assertThat(notification.getDeepLink()).isEqualTo("vidyut://session/1");
        });
    }
}
