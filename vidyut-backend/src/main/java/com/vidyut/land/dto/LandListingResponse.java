package com.vidyut.land.dto;

import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.entity.OwnershipType;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandListingResponse {
    private Long id;
    private Long hostUserId;
    private String title;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private double latitude;
    private double longitude;
    private String connectorType;
    private double powerKw;
    private double pricePerKwh;
    private PropertyType propertyType;
    private int availableParkingBays;
    private PowerPhase powerPhase;
    private double availableLoadKw;
    private String operatingHours;
    private OwnershipType ownershipType;
    private String preferredConnectorType;
    private double preferredPowerKw;
    private String photoUrls;
    private String ownershipDocumentUrl;
    private String electricityDocumentUrl;
    private String videoVerificationUrl;
    private String adminReviewNote;
    private String verificationStage;
    private boolean discoverable;
    private LandListingStatus status;
    private LocalDateTime createdAt;
}
