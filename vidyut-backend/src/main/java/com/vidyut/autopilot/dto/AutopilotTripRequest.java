package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTripRequest {

    @NotNull(message = "Vehicle is required")
    private Long vehicleId;

    @NotBlank(message = "Origin is required")
    private String origin;

    @NotBlank(message = "Destination is required")
    private String destination;

    @Size(max = 1200, message = "Goal must be 1200 characters or fewer")
    private String goal;

    private String tripPurpose;

    private String arrivalDeadline;

    @Builder.Default
    private String optimizeFor = "TIME";

    @Builder.Default
    private String autonomyMode = "ASK_BEFORE_ACTIONS";

    @DecimalMin(value = "1", message = "Battery must be at least 1%")
    @DecimalMax(value = "100", message = "Battery cannot exceed 100%")
    private double currentBatteryPercent;

    @DecimalMin(value = "5", message = "Minimum arrival battery must be at least 5%")
    @DecimalMax(value = "50", message = "Minimum arrival battery cannot exceed 50%")
    private double minimumArrivalBatteryPercent;

    @DecimalMin(value = "1", message = "Charging budget must be greater than zero")
    private double maximumChargingBudget;

    private String idempotencyKey;
}
