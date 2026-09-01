package com.vidyut.notification.dto;

import com.vidyut.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationPreferenceRequest {
    @NotNull
    private NotificationType type;
    private boolean enabled;
}
