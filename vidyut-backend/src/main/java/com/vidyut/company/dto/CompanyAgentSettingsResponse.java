package com.vidyut.company.dto;

import com.vidyut.company.entity.CompanyAgentMode;

public record CompanyAgentSettingsResponse(
        CompanyAgentMode mode,
        double maxPriceChangePercent,
        boolean autoDisableFaultyChargers,
        boolean autoCreateMaintenanceTickets
) {}
