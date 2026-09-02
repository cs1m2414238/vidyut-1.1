package com.vidyut.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_work_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_work_item_scope_key", columnNames = {"account_id", "workspace", "work_key"}),
        @UniqueConstraint(name = "uk_agent_work_item_idempotency", columnNames = {"account_id", "workspace", "idempotency_key"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentWorkItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentWorkspace workspace;

    @Column(nullable = false, length = 180)
    private String workKey;

    @Column(nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String correlationId;

    @Column(length = 100)
    private String objectiveId;

    @Column(length = 100)
    private String actionBundleId;

    @Column(length = 100)
    private String requestId;

    @Column(nullable = false, length = 60)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AgentWorkStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentWorkPriority priority;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1500)
    private String detail;

    @Column(length = 1000)
    private String whatHappened;

    @Column(length = 1000)
    private String whyItMatters;

    @Column(length = 1000)
    private String alreadyDone;

    @Column(length = 1000)
    private String proposedAction;

    @Column(length = 1000)
    private String approvalReason;

    @Column(length = 1000)
    private String expectedImpact;

    @Column(length = 80)
    private String actionType;

    @Column(length = 5000)
    private String actionPayloadJson;

    @Column(length = 5000)
    private String expectedStateJson;

    @Column(length = 50)
    private String resourceType;

    private Long resourceId;

    private Long sourceEventId;

    @Column(length = 1500)
    private String resultSummary;

    @Column(length = 5000)
    private String executionResultJson;

    @Column(length = 1500)
    private String failureReason;

    @Builder.Default
    @Column(nullable = false)
    private int retryCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private int maxRetries = 3;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    private LocalDateTime preparedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime executionStartedAt;

    private LocalDateTime executedAt;

    private LocalDateTime expiresAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private long version = 0;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (status == AgentWorkStatus.DONE || status == AgentWorkStatus.COMPLETED
                || status == AgentWorkStatus.FAILED || status == AgentWorkStatus.STALE
                || status == AgentWorkStatus.CANCELLED) {
            if (completedAt == null) completedAt = updatedAt;
        } else {
            completedAt = null;
        }
    }
}
