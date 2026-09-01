package com.vidyut.marketplace.dto;

import jakarta.validation.constraints.*;

public record ServiceAreaRequest(
        @NotBlank String city,
        @NotBlank String state,
        String pincode,
        Double latitude,
        Double longitude,
        @DecimalMin("1") @DecimalMax("1000") Double radiusKm,
        Boolean installationAvailable,
        Boolean maintenanceAvailable,
        @DecimalMin("0") Double surveyFee,
        @Min(1) @Max(365) Integer typicalInstallationDays,
        Boolean active
) {}
