package com.vidyut.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 180)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AgentEventType eventType;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    private Long actorAccountId;

    @Column(nullable = false, length = 100)
    private String correlationId;

    @Column(nullable = false, length = 5000)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private AgentOutboxStatus status = AgentOutboxStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private int maxAttempts = 5;

    @Column(length = 1500)
    private String lastError;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime availableAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime publishedAt;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private long version = 0;

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
