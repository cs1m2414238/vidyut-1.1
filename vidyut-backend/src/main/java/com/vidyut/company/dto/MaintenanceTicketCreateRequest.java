package com.vidyut.company.dto;

import com.vidyut.company.entity.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaintenanceTicketCreateRequest(
        @NotNull Long chargerId,
        @NotNull MaintenancePriority priority,
        @NotBlank @Size(max = 1500) String issue,
        @Size(max = 150) String assignedTo
) {}
