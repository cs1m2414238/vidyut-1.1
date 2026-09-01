package com.vidyut.company.dto;

import com.vidyut.station.entity.ChargerStatus;

import java.time.LocalDateTime;

public record ManagedChargerResponse(
        Long id,
        Long stationId,
        String stationName,
        String city,
        String chargerCode,
        String connectorType,
        double powerKw,
        ChargerStatus status,
        boolean available,
        boolean maintenanceMode,
        int healthScore,
        String faultCode,
        LocalDateTime lastHeartbeat,
        String relationship
) {}
