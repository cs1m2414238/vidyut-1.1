package com.vidyut.host.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HostAgentActionRequest {
    @NotBlank
    private String action;
    private Long stationId;
    private Long connectorId;
    private Long propertyId;
    private java.util.Map<String, Object> payload;
    private boolean approved;
    private Long workItemId;
    private String idempotencyKey;
    private String correlationId;
    private String expectedStatus;
    private String expectedPropertyStatus;
}
