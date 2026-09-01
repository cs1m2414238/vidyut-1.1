package com.vidyut.autopilot.dto;

import com.vidyut.autopilot.entity.AutopilotStopStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotStopResponse {
    private Double latitude;
    private Double longitude;
    private Long id;
    private int sequenceNumber;
    private Long stationId;
    private Long bookingId;
    private Long connectorId;
    private String chargerCode;
    private String stationName;
    private String stationAddress;
    private String connectorType;
    private double powerKw;
    private double effectivePowerKw;
    private double distanceFromOriginKm;
    private double routeOffsetKm;
    private double arrivalBatteryPercent;
    private double targetBatteryPercent;
    private int estimatedWaitMinutes;
    private int chargingMinutes;
    private int connectionMinutes;
    private double estimatedCost;
    private boolean demoData;
    private String selectionReason;
    private String timingScore;
    private String timingLabel;
    private AutopilotStopStatus status;

    private String selectionType;
    private Long replacesStationId;
    private String replacesStationName;
    private String rerouteReason;
    private Double additionalDistanceKm;
    private Integer additionalMinutes;
    private Double additionalCost;

    private String removalReason;
    private Long replacedByStationId;
    private String replacedByStationName;
    private Integer originalStopIndex;
}
