package com.vidyut.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private String title;
    private String message;

    @Column(length = 500)
    private String deepLink;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private boolean isRead;

    @Builder.Default
    @Column(nullable = false)
    private boolean critical = false;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
