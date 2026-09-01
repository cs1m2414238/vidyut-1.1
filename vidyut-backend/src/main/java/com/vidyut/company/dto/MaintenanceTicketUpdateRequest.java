package com.vidyut.company.dto;

import com.vidyut.company.entity.MaintenancePriority;
import com.vidyut.company.entity.MaintenanceTicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceTicketUpdateRequest(
        @NotNull MaintenanceTicketStatus status,
        MaintenancePriority priority,
        @Size(max = 150) String assignedTo,
        @Size(max = 2000) String resolutionNote,
        boolean restoreChargerOnline
) {}
