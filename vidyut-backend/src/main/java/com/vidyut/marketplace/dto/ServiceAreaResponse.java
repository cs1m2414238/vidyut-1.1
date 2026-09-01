package com.vidyut.marketplace.dto;

public record ServiceAreaResponse(
        Long id, String city, String state, String pincode,
        Double latitude, Double longitude, double radiusKm,
        boolean installationAvailable, boolean maintenanceAvailable,
        double surveyFee, int typicalInstallationDays, boolean active
) {}
