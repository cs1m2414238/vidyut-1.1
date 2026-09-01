package com.vidyut.notification.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.dto.NotificationPreferenceRequest;
import com.vidyut.notification.dto.NotificationPreferenceResponse;
import com.vidyut.notification.dto.PushDeviceRequest;
import com.vidyut.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserUtil currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications() {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationsForUser(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.unreadCount(currentUser.getCurrentAccountId())));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(
                currentUser.getCurrentAccountId(), notificationId)));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        notificationService.markAllRead(currentUser.getCurrentAccountId());
        return ResponseEntity.ok(ApiResponse.success("Notifications marked read", null));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> preferences() {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getPreferences(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreference(
            @Valid @RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.updatePreference(
                currentUser.getCurrentAccountId(), request.getType(), request.isEnabled())));
    }

    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<Void>> registerDevice(@Valid @RequestBody PushDeviceRequest request) {
        notificationService.registerDevice(currentUser.getCurrentAccountId(), request.getToken(), request.getPlatform());
        return ResponseEntity.ok(ApiResponse.success("Push device registered", null));
    }
}
