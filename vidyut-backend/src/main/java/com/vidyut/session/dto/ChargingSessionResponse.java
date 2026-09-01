package com.vidyut.session.dto;

import com.vidyut.session.entity.ChargingSessionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingSessionResponse {
    private Long id;
    private Long bookingId;
    private Long stationId;
    private Long connectorId;
    private String stationName;
    private Long vehicleId;
    private String vehicleName;
    private ChargingSessionStatus status;
    private String paymentStatus;
    private double powerKw;
    private double energyKwh;
    private double cost;
    private double co2SavedKg;
    private int startBatteryPercent;
    private int currentBatteryPercent;
    private int targetBatteryPercent;
    private String telemetrySource;
    private LocalDateTime startedAt;
    private LocalDateTime estimatedCompletionAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
