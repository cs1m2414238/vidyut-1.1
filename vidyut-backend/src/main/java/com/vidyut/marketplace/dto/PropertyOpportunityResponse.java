package com.vidyut.marketplace.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PropertyOpportunityResponse(
        Long id, String title, String address, String city, String state, String pincode,
        double latitude, double longitude, String propertyType, int parkingBays,
        String powerPhase, double availableLoadKw, String operatingHours,
        String ownershipType, String preferredConnectorType, double preferredPowerKw,
        String photoUrls, String siteVideoUrl, String matchedBy, Double distanceKm,
        String hostDisplayName, String hostBio, LocalDateTime hostMemberSince,
        double hostRating, int hostReviewCount, int hostTrustScore,
        int verifiedProperties, int successfulPartnerships, int disputes,
        int propertyScore, int commercialScore, String verificationRisk, String verificationMethod,
        boolean identityVerified, boolean ownershipVerified, boolean electricityVerified,
        boolean videoVerified, boolean physicalInspectionRecommended,
        List<HostReviewSummaryResponse> recentHostReviews
) {}
