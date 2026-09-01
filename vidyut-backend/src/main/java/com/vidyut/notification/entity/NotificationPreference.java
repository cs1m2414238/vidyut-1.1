package com.vidyut.notification.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_preferences", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_preference_user_type", columnNames = {"user_id", "type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;
}
