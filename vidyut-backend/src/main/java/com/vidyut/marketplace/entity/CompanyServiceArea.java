package com.vidyut.marketplace.entity;

import com.vidyut.company.entity.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_service_areas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyServiceArea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(length = 12)
    private String pincode;

    private Double latitude;
    private Double longitude;

    @Builder.Default
    private double radiusKm = 50;

    @Builder.Default
    private boolean installationAvailable = true;

    @Builder.Default
    private boolean maintenanceAvailable = true;

    @Builder.Default
    private double surveyFee = 0;

    @Builder.Default
    private int typicalInstallationDays = 14;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
