package com.vidyut.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentWorkspace workspace;

    private Long workItemId;

    @Column(nullable = false, length = 100)
    private String correlationId;

    @Column(nullable = false, length = 60)
    private String activityType;

    @Column(nullable = false, length = 240)
    private String summary;

    @Column(length = 1500)
    private String detail;

    @Column(length = 5000)
    private String metadataJson;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();
}
