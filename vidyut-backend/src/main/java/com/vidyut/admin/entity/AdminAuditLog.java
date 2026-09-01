package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs", indexes = @Index(name = "idx_admin_audit_created", columnList = "created_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long adminAccountId;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, length = 80) private String resourceType;
    @Column(length = 80) private String resourceId;
    @Column(nullable = false, length = 1200) private String summary;
    @Column(length = 2000) private String previousValue;
    @Column(length = 2000) private String newValue;
    @Column(length = 1200) private String reason;
    @Column(name = "created_at", nullable = false)
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
