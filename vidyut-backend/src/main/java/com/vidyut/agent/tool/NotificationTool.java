package com.vidyut.agent.tool;

import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationTool {

    private final NotificationService notificationService;

    public void triggerAgentAlert(Long userId, String alertMessage) {
        notificationService.sendNotification(userId, "🤖 Vidyut AI Alert", alertMessage, NotificationType.SYSTEM_ALERT);
    }
}
