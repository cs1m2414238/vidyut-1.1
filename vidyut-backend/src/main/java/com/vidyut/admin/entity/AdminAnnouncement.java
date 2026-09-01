package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_announcements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnnouncement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, length = 2000) private String message;
    @Column(nullable = false, length = 30) private String audience;
    @Column(nullable = false, length = 20) private String severity;
    @Column(length = 120) private String targetState;
    @Column(length = 120) private String targetCity;
    private Long targetAccountId;
    @Builder.Default private boolean active = true;
    private Long createdByAdminId;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
