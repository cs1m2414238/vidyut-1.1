package com.vidyut.company.dto;

import com.vidyut.company.entity.MaintenancePriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanyAgentActionRequest(
        @NotNull CompanyAgentActionType action,
        Long chargerId,
        Long stationId,
        Double proposedPricePerKwh,
        MaintenancePriority priority,
        @Size(max = 500) String reason,
        boolean approved,
        com.vidyut.station.entity.ChargerStatus expectedStatus,
        Double expectedPricePerKwh,
        Long workItemId,
        @Size(max = 100) String idempotencyKey,
        @Size(max = 100) String correlationId
) {
    public CompanyAgentActionRequest(CompanyAgentActionType action, Long chargerId, Long stationId,
            Double proposedPricePerKwh, MaintenancePriority priority, String reason, boolean approved) {
        this(action, chargerId, stationId, proposedPricePerKwh, priority, reason, approved,
                null, null, null, null, null);
    }

    public CompanyAgentActionRequest(CompanyAgentActionType action, Long chargerId, Long stationId,
            Double proposedPricePerKwh, MaintenancePriority priority, String reason, boolean approved,
            com.vidyut.station.entity.ChargerStatus expectedStatus) {
        this(action, chargerId, stationId, proposedPricePerKwh, priority, reason, approved,
                expectedStatus, null, null, null, null);
    }
}
