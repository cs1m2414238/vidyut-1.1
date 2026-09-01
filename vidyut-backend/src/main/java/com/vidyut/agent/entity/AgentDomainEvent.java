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
@Table(name = "agent_domain_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDomainEvent {
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

    @Column(nullable = false, length = 5000)
    private String payloadJson;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();
}
