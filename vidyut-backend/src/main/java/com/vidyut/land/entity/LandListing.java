package com.vidyut.land.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "land_listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostUserId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String address;

    private String city;
    private String state;
    private String pincode;

    private double latitude;
    private double longitude;

    private String connectorType;
    private double powerKw;
    private double pricePerKwh;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PropertyType propertyType = PropertyType.OTHER;

    @Builder.Default
    private int availableParkingBays = 1;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PowerPhase powerPhase = PowerPhase.NOT_SURE;

    @Builder.Default
    private double availableLoadKw = 0;

    private String operatingHours;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OwnershipType ownershipType = OwnershipType.OWNED;

    private String preferredConnectorType;
    private double preferredPowerKw;

    @Column(length = 2000)
    private String photoUrls;

    @Column(length = 1000)
    private String ownershipDocumentUrl;

    @Column(length = 1000)
    private String electricityDocumentUrl;

    @Column(length = 1000)
    private String videoVerificationUrl;

    @Column(length = 800)
    private String adminReviewNote;

    @Builder.Default
    @Column(nullable = false, length = 40)
    private String verificationStage = "SUBMITTED";

    @Column(length = 20)
    private String verificationRisk;

    @Column(length = 60)
    private String verificationMethod;

    @Builder.Default
    private boolean videoVerified = false;

    @Column(length = 40)
    private String physicalInspectionStatus;

    private LocalDateTime inspectionScheduledAt;

    @Column(length = 1200)
    private String inspectionNote;

    private Integer propertyScore;

    @Builder.Default
    private boolean discoverable = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LandListingStatus status = LandListingStatus.PENDING_APPROVAL;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
