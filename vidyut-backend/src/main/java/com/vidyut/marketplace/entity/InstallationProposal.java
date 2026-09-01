package com.vidyut.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "installation_proposals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationProposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_request_id", nullable = false, unique = true)
    private InstallationRequest request;

    private double equipmentTotal;
    private double installationTotal;
    private Double monthlyLease;
    private Double hostRevenueSharePercent;
    private Double companyRevenueSharePercent;
    private LocalDate validUntil;
    private int estimatedInstallationDays;

    @Column(length = 2000)
    private String terms;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
