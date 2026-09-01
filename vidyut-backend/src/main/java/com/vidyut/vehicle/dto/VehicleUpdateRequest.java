package com.vidyut.vehicle.dto;

import com.vidyut.station.entity.ConnectorType;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleUpdateRequest {

    @Min(value = 0, message = "Battery percentage cannot be below zero")
    @Max(value = 100, message = "Battery percentage cannot exceed 100")
    private Integer batteryPercent;

    @DecimalMin(value = "0.0", message = "Remaining range cannot be negative")
    private Double remainingRangeKm;

    private VehicleConnectionStatus connectionStatus;
    private Boolean charging;
    private Boolean bluetoothSupported;
    private Boolean androidAutoSupported;
    private Boolean appleCarPlaySupported;
    private String bluetoothDeviceName;
    private String bluetoothDeviceId;
    private String bluetoothServiceUuid;
    private Boolean btSessionControlEnabled;
    private Boolean btSimulatorEnabled;
    private String lastChargingStation;
    private String lastChargingAddress;
    private LocalDateTime lastChargedAt;
    private VehicleTelemetrySource telemetrySource;

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
