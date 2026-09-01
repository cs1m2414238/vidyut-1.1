package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_settlements", indexes = @Index(name = "idx_admin_settlement_status", columnList = "status,updated_at"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSettlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Long paymentId;
    private Long bookingId;
    private Long stationId;
    @Column(length = 180) private String stationName;
    @Column(length = 40) private String ownershipType;
    private double grossAmount;
    private double platformAmount;
    private double companyAmount;
    private double hostAmount;
    private double taxesAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SettlementStatus status;
    @Column(length = 1500) private String disputeNote;
    private Long processedByAdminId;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime settledAt;

    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
