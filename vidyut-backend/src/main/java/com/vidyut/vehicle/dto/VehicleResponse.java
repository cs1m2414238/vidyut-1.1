package com.vidyut.vehicle.dto;

import com.vidyut.station.entity.ConnectorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private Long id;
    private Long userId;
    private String makeAndModel;
    private String registrationNumber;
    private String batteryCapacity;
    private String connectorType;
    private Set<ConnectorType> supportedConnectors;
    private Double efficiencyWhPerKm;
    private Double maxAcChargePowerKw;
    private Double maxDcChargePowerKw;
    private Double chargingEfficiency;
    private VehicleConnectionStatus connectionStatus;
    private Integer batteryPercent;
    private Double remainingRangeKm;
    private Boolean charging;
    private Boolean bluetoothSupported;
    private Boolean androidAutoSupported;
    private Boolean appleCarPlaySupported;
    private String bluetoothDeviceName;
    private String bluetoothDeviceId;
    private String bluetoothServiceUuid;
    private boolean btSessionControlEnabled;
    private boolean btSimulatorEnabled;
    private String lastChargingStation;
    private String lastChargingAddress;
    private LocalDateTime lastChargedAt;
    private VehicleTelemetrySource telemetrySource;
    private LocalDateTime telemetryUpdatedAt;
}
