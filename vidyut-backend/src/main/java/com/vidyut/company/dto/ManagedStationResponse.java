package com.vidyut.company.dto;

import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.entity.StationOwnershipType;

public record ManagedStationResponse(
        Long id,
        String name,
        String city,
        String address,
        Long hostUserId,
        String propertyOwnerName,
        String operatorCompanyName,
        StationOwnershipType ownershipType,
        Long hostPartnershipId,
        String relationship,
        StationStatus status,
        int chargerCount,
        int onlineChargers,
        int faultedChargers
) {}
