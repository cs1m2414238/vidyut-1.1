package com.vidyut.notification.service;

import com.vidyut.notification.entity.Notification;
import com.vidyut.notification.entity.PushDevice;
import com.vidyut.notification.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExpoPushGateway {
    private static final Logger log = LoggerFactory.getLogger(ExpoPushGateway.class);
    private final PushDeviceRepository deviceRepository;

    @Value("${vidyut.notifications.push-enabled:false}")
    private boolean pushEnabled;

    public void deliver(Notification notification) {
        if (!pushEnabled) return;
        RestClient client = RestClient.create("https://exp.host/--/api/v2/push");
        for (PushDevice device : deviceRepository.findByUserIdAndEnabledTrue(notification.getUserId())) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("to", device.getToken());
                payload.put("title", notification.getTitle());
                payload.put("body", notification.getMessage());
                payload.put("sound", "default");
                payload.put("data", Map.of(
                        "notificationId", notification.getId(),
                        "type", notification.getType().name(),
                        "url", notification.getDeepLink() == null ? "vidyut://notifications" : notification.getDeepLink()));
                client.post().uri("/send").body(payload).retrieve().toBodilessEntity();
            } catch (RuntimeException error) {
                log.warn("Push delivery failed for device {}: {}", device.getId(), error.getMessage());
            }
        }
    }
}
