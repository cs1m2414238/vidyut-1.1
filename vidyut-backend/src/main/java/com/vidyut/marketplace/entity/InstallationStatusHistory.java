package com.vidyut.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "installation_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_request_id", nullable = false)
    private InstallationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InstallationStatus status;

    @Column(nullable = false)
    private Long actorAccountId;

    @Column(length = 1000)
    private String note;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
