package com.vidyut.vehicle.service;

import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.vehicle.dto.VehicleCreateRequest;
import com.vidyut.vehicle.dto.VehicleResponse;
import com.vidyut.vehicle.dto.VehicleUpdateRequest;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.repository.VehicleAutoRechargeRuleRepository;
import com.vidyut.wallet.repository.VehicleWalletRepository;
import com.vidyut.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleAutoRechargeRuleRepository autoRechargeRuleRepository;
    private final VehicleWalletRepository vehicleWalletRepository;

    @Override
    public VehicleResponse addVehicle(Long userId, VehicleCreateRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException("Vehicle already registered with number: " + request.getRegistrationNumber());
        }

        Set<ConnectorType> supportedConnectors = supportedConnectors(
                request.getSupportedConnectors(), request.getConnectorType());
        String primaryConnector = request.getConnectorType() == null || request.getConnectorType().isBlank()
                ? supportedConnectors.iterator().next().name()
                : normalizeConnector(request.getConnectorType()).name();
        Vehicle vehicle = Vehicle.builder()
                .userId(userId)
                .makeAndModel(request.getMakeAndModel())
                .registrationNumber(request.getRegistrationNumber())
                .batteryCapacity(request.getBatteryCapacity())
                .connectorType(primaryConnector)
                .supportedConnectors(supportedConnectors)
                .efficiencyWhPerKm(request.getEfficiencyWhPerKm() != null
                        ? request.getEfficiencyWhPerKm() : 140.0)
                .maxAcChargePowerKw(request.getMaxAcChargePowerKw() != null
                        ? request.getMaxAcChargePowerKw() : 7.2)
                .maxDcChargePowerKw(request.getMaxDcChargePowerKw() != null
                        ? request.getMaxDcChargePowerKw() : 50.0)
                .chargingEfficiency(request.getChargingEfficiency() != null
                        ? request.getChargingEfficiency() : 0.90)
                .connectionStatus(VehicleConnectionStatus.UNKNOWN)
                .telemetrySource(VehicleTelemetrySource.NOT_AVAILABLE)
                .build();

        return mapToResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public VehicleResponse getVehicleById(Long id, Long userId) {
        return mapToResponse(findOwnedVehicle(id, userId));
    }

    @Override
    public List<VehicleResponse> getVehiclesByUserId(Long userId) {
        return vehicleRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long id, Long userId, VehicleUpdateRequest request) {
        Vehicle vehicle = findOwnedVehicle(id, userId);
        if (request.getBatteryPercent() != null) vehicle.setBatteryPercent(request.getBatteryPercent());
        if (request.getRemainingRangeKm() != null) vehicle.setRemainingRangeKm(request.getRemainingRangeKm());
        if (request.getConnectionStatus() != null) vehicle.setConnectionStatus(request.getConnectionStatus());
        if (request.getCharging() != null) vehicle.setCharging(request.getCharging());
        if (request.getBluetoothSupported() != null) vehicle.setBluetoothSupported(request.getBluetoothSupported());
        if (request.getAndroidAutoSupported() != null) vehicle.setAndroidAutoSupported(request.getAndroidAutoSupported());
        if (request.getAppleCarPlaySupported() != null) vehicle.setAppleCarPlaySupported(request.getAppleCarPlaySupported());
        if (request.getBluetoothDeviceName() != null) vehicle.setBluetoothDeviceName(clean(request.getBluetoothDeviceName()));
        if (request.getBluetoothDeviceId() != null) vehicle.setBluetoothDeviceId(clean(request.getBluetoothDeviceId()));
        if (request.getBluetoothServiceUuid() != null) vehicle.setBluetoothServiceUuid(clean(request.getBluetoothServiceUuid()));
        if (request.getBtSessionControlEnabled() != null) vehicle.setBtSessionControlEnabled(request.getBtSessionControlEnabled());
        if (request.getBtSimulatorEnabled() != null) vehicle.setBtSimulatorEnabled(request.getBtSimulatorEnabled());
        if (request.getLastChargingStation() != null) vehicle.setLastChargingStation(clean(request.getLastChargingStation()));
        if (request.getLastChargingAddress() != null) vehicle.setLastChargingAddress(clean(request.getLastChargingAddress()));
        if (request.getLastChargedAt() != null) vehicle.setLastChargedAt(request.getLastChargedAt());
        if (request.getTelemetrySource() != null) vehicle.setTelemetrySource(request.getTelemetrySource());
        if (request.getSupportedConnectors() != null && !request.getSupportedConnectors().isEmpty()) {
            vehicle.setSupportedConnectors(new LinkedHashSet<>(request.getSupportedConnectors()));
            if (!vehicle.getSupportedConnectors().contains(normalizeConnector(vehicle.getConnectorType()))) {
                vehicle.setConnectorType(vehicle.getSupportedConnectors().iterator().next().name());
            }
        }
        if (request.getEfficiencyWhPerKm() != null) vehicle.setEfficiencyWhPerKm(request.getEfficiencyWhPerKm());
        if (request.getMaxAcChargePowerKw() != null) vehicle.setMaxAcChargePowerKw(request.getMaxAcChargePowerKw());
        if (request.getMaxDcChargePowerKw() != null) vehicle.setMaxDcChargePowerKw(request.getMaxDcChargePowerKw());
        if (request.getChargingEfficiency() != null) vehicle.setChargingEfficiency(request.getChargingEfficiency());
        vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
        return mapToResponse(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        if (vehicle.getRegistrationNumber() != null && (
                vehicle.getRegistrationNumber().toUpperCase().startsWith("DEMO-EV-") ||
                vehicle.getRegistrationNumber().toUpperCase().startsWith("PRI-EV-"))) {
            throw new BadRequestException("Core seeded demo EVs (" + vehicle.getRegistrationNumber() + ") cannot be deleted.");
        }
        removeWalletConfiguration(vehicle.getUserId(), id);
        vehicleRepository.delete(vehicle);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id, Long userId) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        if (vehicle.getRegistrationNumber() != null && (
                vehicle.getRegistrationNumber().toUpperCase().startsWith("DEMO-EV-") ||
                vehicle.getRegistrationNumber().toUpperCase().startsWith("PRI-EV-"))) {
            throw new BadRequestException("Core seeded demo EVs (" + vehicle.getRegistrationNumber() + ") cannot be deleted.");
        }
        removeWalletConfiguration(userId, id);
        vehicleRepository.delete(vehicle);
    }

    private void removeWalletConfiguration(Long userId, Long vehicleId) {
        vehicleWalletRepository.findByUserIdAndVehicle_Id(userId, vehicleId).ifPresent(wallet -> {
            if (wallet.getBalance() > 0.009) {
                throw new BadRequestException("Use or transfer the remaining vehicle wallet balance before removing this EV");
            }
        });
        autoRechargeRuleRepository.deleteByUserIdAndVehicle_Id(userId, vehicleId);
        vehicleWalletRepository.deleteByUserIdAndVehicle_Id(userId, vehicleId);
    }

    private VehicleResponse mapToResponse(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .userId(v.getUserId())
                .makeAndModel(v.getMakeAndModel())
                .registrationNumber(v.getRegistrationNumber())
                .batteryCapacity(v.getBatteryCapacity())
                .connectorType(v.getConnectorType())
                .supportedConnectors(effectiveSupportedConnectors(v))
                .efficiencyWhPerKm(v.getEfficiencyWhPerKm())
                .maxAcChargePowerKw(v.getMaxAcChargePowerKw())
                .maxDcChargePowerKw(v.getMaxDcChargePowerKw())
                .chargingEfficiency(v.getChargingEfficiency())
                .connectionStatus(v.getConnectionStatus() != null ? v.getConnectionStatus() : VehicleConnectionStatus.UNKNOWN)
                .batteryPercent(v.getBatteryPercent())
                .remainingRangeKm(v.getRemainingRangeKm())
                .charging(v.getCharging())
                .bluetoothSupported(v.getBluetoothSupported())
                .androidAutoSupported(v.getAndroidAutoSupported())
                .appleCarPlaySupported(v.getAppleCarPlaySupported())
                .bluetoothDeviceName(v.getBluetoothDeviceName())
                .bluetoothDeviceId(v.getBluetoothDeviceId())
                .bluetoothServiceUuid(v.getBluetoothServiceUuid())
                .btSessionControlEnabled(v.isBtSessionControlEnabled())
                .btSimulatorEnabled(v.isBtSimulatorEnabled())
                .lastChargingStation(v.getLastChargingStation())
                .lastChargingAddress(v.getLastChargingAddress())
                .lastChargedAt(v.getLastChargedAt())
                .telemetrySource(v.getTelemetrySource() != null ? v.getTelemetrySource() : VehicleTelemetrySource.NOT_AVAILABLE)
                .telemetryUpdatedAt(v.getTelemetryUpdatedAt())
                .build();
    }

    private Vehicle findOwnedVehicle(Long id, Long userId) {
        return vehicleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
    }

    private String clean(String value) {
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Set<ConnectorType> supportedConnectors(
            Set<ConnectorType> requested,
            String legacyConnector
    ) {
        if (requested != null && !requested.isEmpty()) {
            return new LinkedHashSet<>(requested);
        }
        return new LinkedHashSet<>(Set.of(normalizeConnector(legacyConnector)));
    }

    private Set<ConnectorType> effectiveSupportedConnectors(Vehicle vehicle) {
        if (vehicle.getSupportedConnectors() != null && !vehicle.getSupportedConnectors().isEmpty()) {
            return new LinkedHashSet<>(vehicle.getSupportedConnectors());
        }
        return new LinkedHashSet<>(Set.of(normalizeConnector(vehicle.getConnectorType())));
    }

    private ConnectorType normalizeConnector(String connector) {
        if (connector == null || connector.isBlank()) return ConnectorType.CCS2;
        String normalized = connector.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (normalized.contains("BHARATDC001") || normalized.equals("GBT")) return ConnectorType.GB_T;
        if (normalized.contains("CHADEMO")) return ConnectorType.CHADEMO;
        if (normalized.contains("CCS2")) return ConnectorType.CCS2;
        if (normalized.contains("TYPE1")) return ConnectorType.TYPE1;
        if (normalized.contains("TYPE2")) return ConnectorType.TYPE2;
        return ConnectorType.CCS2;
    }
}
