package com.vidyut.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyIntentParseResponse {
    private String origin;
    private String destination;
    private Double currentBatteryPercent;
    private Double minimumArrivalBatteryPercent;
    private Double maximumChargingBudget;
    private String arrivalDeadline;
    private String optimizeFor;
    private String autonomyMode;
    private String tripPurpose;
    private List<String> recognizedFields;
}
