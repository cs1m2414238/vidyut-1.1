package com.vidyut.marketplace.dto;

import java.util.List;

public record MarketplaceCompanyResponse(
        Long id, String companyName, String website, String supportEmail,
        String supportPhone, String verificationStatus, String matchedBy,
        Double distanceKm, List<ChargerProductResponse> products
) {}
