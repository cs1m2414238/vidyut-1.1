package com.vidyut.marketplace.entity;

import com.vidyut.company.entity.Company;
import com.vidyut.land.entity.LandListing;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "installation_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstallationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "land_listing_id", nullable = false)
    private LandListing property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charger_product_id", nullable = false)
    private ChargerProduct product;

    @Builder.Default
    private int quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BusinessModel businessModel;

    private Double budget;
    private LocalDate targetInstallationDate;

    @Column(length = 1500)
    private String hostMessage;

    @Column(length = 1500)
    private String companyNote;

    private LocalDate scheduledSurveyAt;
    private LocalDate scheduledInstallationAt;
    private Long stationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private InstallationStatus status = InstallationStatus.REQUESTED;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
