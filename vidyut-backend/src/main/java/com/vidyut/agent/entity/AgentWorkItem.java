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
@Table(name = "agent_work_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_work_item_scope_key", columnNames = {"account_id", "workspace", "work_key"}))
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

    @Column(length = 80)
    private String actionType;

    @Column(length = 5000)
    private String actionPayloadJson;

    @Column(length = 50)
    private String resourceType;

    private Long resourceId;

    private Long sourceEventId;

    @Column(length = 1500)
    private String resultSummary;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private long version = 0;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (status == AgentWorkStatus.DONE || status == AgentWorkStatus.FAILED) {
            if (completedAt == null) completedAt = updatedAt;
        } else {
            completedAt = null;
        }
    }
}
