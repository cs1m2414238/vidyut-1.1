package com.vidyut.company.dto;

import com.vidyut.company.entity.CompanyAgentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompanyAgentSettingsRequest(
        @NotNull CompanyAgentMode mode,
        @Min(0) @Max(25) double maxPriceChangePercent,
        boolean autoDisableFaultyChargers,
        boolean autoCreateMaintenanceTickets
) {}
