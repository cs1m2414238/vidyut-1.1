package com.vidyut.notification.dto;

import com.vidyut.notification.entity.NotificationType;

public record NotificationPreferenceResponse(NotificationType type, boolean enabled, boolean critical) {}
