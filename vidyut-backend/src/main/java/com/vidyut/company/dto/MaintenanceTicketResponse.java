package com.vidyut.company.dto;

import com.vidyut.company.entity.MaintenancePriority;
import com.vidyut.company.entity.MaintenanceTicketStatus;

import java.time.LocalDateTime;

public record MaintenanceTicketResponse(
        Long id,
        Long chargerId,
        String chargerCode,
        Long stationId,
        String stationName,
        String city,
        MaintenancePriority priority,
        MaintenanceTicketStatus status,
        String issue,
        String assignedTo,
        String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt
) {}
