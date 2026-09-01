package com.vidyut.vehicle.dto;

import com.vidyut.station.entity.ConnectorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleCreateRequest {

    @NotBlank(message = "Vehicle make and model is required")
    private String makeAndModel;

    @NotBlank(message = "Registration number is required")
    private String registrationNumber;

    private String batteryCapacity;

    private String connectorType;

    private Set<ConnectorType> supportedConnectors;

    @DecimalMin(value = "50.0", message = "Efficiency must be at least 50 Wh/km")
    @DecimalMax(value = "500.0", message = "Efficiency cannot exceed 500 Wh/km")
    private Double efficiencyWhPerKm;

    @DecimalMin(value = "1.0", message = "Maximum AC charging power must be positive")
    private Double maxAcChargePowerKw;

    @DecimalMin(value = "1.0", message = "Maximum DC charging power must be positive")
    private Double maxDcChargePowerKw;

    @DecimalMin(value = "0.5", message = "Charging efficiency must be at least 0.5")
    @DecimalMax(value = "1.0", message = "Charging efficiency cannot exceed 1.0")
    private Double chargingEfficiency;
}
