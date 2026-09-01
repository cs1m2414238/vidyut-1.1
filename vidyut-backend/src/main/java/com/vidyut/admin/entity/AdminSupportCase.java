package com.vidyut.admin.entity;

import com.vidyut.account.entity.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_support_cases", indexes = {
        @Index(name = "idx_admin_support_status", columnList = "status,updated_at"),
        @Index(name = "idx_admin_support_account", columnList = "account_id,created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSupportCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccountType accountType;
    @Column(nullable = false, length = 50) private String category;
    @Column(nullable = false, length = 180) private String subject;
    @Column(nullable = false, length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SupportCasePriority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SupportCaseStatus status;
    private Long assignedAdminId;
    @Column(length = 1500) private String evidenceNote;
    @Column(length = 1500) private String resolutionNote;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
