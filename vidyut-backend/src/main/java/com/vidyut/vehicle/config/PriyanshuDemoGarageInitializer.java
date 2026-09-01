package com.vidyut.vehicle.config;

import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleConnectionStatus;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class PriyanshuDemoGarageInitializer implements ApplicationRunner {

    private static final String PROFILE_NAME = "Priyanshu Sharma";

    private final EvUserProfileRepository profileRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        profileRepository.findAll().stream()
                .filter(profile -> PROFILE_NAME.equalsIgnoreCase(profile.getFullName().trim()))
                .forEach(profile -> seedGarage(profile.getAccountId()));
    }

    private void seedGarage(Long userId) {
        Map<String, Vehicle> existingByRegistration = vehicleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        vehicle -> vehicle.getRegistrationNumber().toUpperCase(),
                        Function.identity(),
                        (first, ignored) -> first));

        for (DemoVehicle seed : demoVehicles()) {
            Vehicle existing = existingByRegistration.get(seed.registrationNumber());
            if (existing == null) {
                vehicleRepository.save(toVehicle(userId, seed));
            } else if (existing.getUserId().equals(userId)) {
                updateSeededVehicle(existing, seed);
                vehicleRepository.save(existing);
            }
        }
    }

    private Vehicle toVehicle(Long userId, DemoVehicle seed) {
        Vehicle vehicle = Vehicle.builder()
                .userId(userId)
                .registrationNumber(seed.registrationNumber())
                .connectionStatus(VehicleConnectionStatus.CONNECTED)
                .telemetrySource(VehicleTelemetrySource.MANUAL)
                .charging(false)
                .build();
        updateSeededVehicle(vehicle, seed);
        return vehicle;
    }

    private void updateSeededVehicle(Vehicle vehicle, DemoVehicle seed) {
        vehicle.setMakeAndModel(seed.name());
        vehicle.setBatteryCapacity(seed.batteryCapacityKwh() + " kWh");
        vehicle.setConnectorType(seed.primaryConnector().name());
        vehicle.setSupportedConnectors(new LinkedHashSet<>(seed.supportedConnectors()));
        vehicle.setEfficiencyWhPerKm(seed.efficiencyWhPerKm());
        vehicle.setMaxAcChargePowerKw(seed.maxAcChargePowerKw());
        vehicle.setMaxDcChargePowerKw(seed.maxDcChargePowerKw());
        vehicle.setChargingEfficiency(seed.chargingEfficiency());
        vehicle.setBatteryPercent(seed.currentBatteryPercent());
        vehicle.setRemainingRangeKm(round(seed.batteryCapacityKwh()
                * seed.currentBatteryPercent() / 100.0
                / (seed.efficiencyWhPerKm() / 1000.0)));
        vehicle.setConnectionStatus(VehicleConnectionStatus.CONNECTED);
        vehicle.setTelemetrySource(VehicleTelemetrySource.MANUAL);
    }

    private List<DemoVehicle> demoVehicles() {
        return List.of(
                new DemoVehicle("Tata Nexon EV 45", "UP78NX0045", 45, 155,
                        7.2, 60, 0.90, 72, ConnectorType.CCS2,
                        List.of(ConnectorType.CCS2, ConnectorType.TYPE2)),
                new DemoVehicle("Tata Tigor EV", "UP78TG0026", 26, 135,
                        3.3, 30, 0.90, 84, ConnectorType.CCS2,
                        List.of(ConnectorType.CCS2, ConnectorType.TYPE2)),
                new DemoVehicle("Mahindra BE 6 79 kWh", "UP78BE6079", 79, 175,
                        11, 175, 0.92, 76, ConnectorType.CCS2,
                        List.of(ConnectorType.CCS2, ConnectorType.TYPE2)),
                new DemoVehicle("Mahindra XEV 9e 79 kWh", "UP78XEV079", 79, 190,
                        11, 175, 0.92, 68, ConnectorType.CCS2,
                        List.of(ConnectorType.CCS2, ConnectorType.TYPE2)),
                new DemoVehicle("Demo CHAdeMO Test Vehicle", "DEMOCH0001", 40, 155,
                        6.6, 50, 0.88, 85, ConnectorType.CHADEMO,
                        List.of(ConnectorType.CHADEMO)),
                new DemoVehicle("Demo GB/T Test Vehicle", "DEMOGB0001", 55, 165,
                        7.2, 90, 0.89, 68, ConnectorType.GB_T,
                        List.of(ConnectorType.GB_T)),
                new DemoVehicle("Demo Type 2 AC Test Vehicle", "DEMOT20001", 32, 135,
                        11, 11, 0.90, 90, ConnectorType.TYPE2,
                        List.of(ConnectorType.TYPE2)),
                new DemoVehicle("Demo Type 1 AC Test Vehicle", "DEMOT10001", 32, 145,
                        7.2, 7.2, 0.88, 88, ConnectorType.TYPE1,
                        List.of(ConnectorType.TYPE1))
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record DemoVehicle(
            String name,
            String registrationNumber,
            double batteryCapacityKwh,
            double efficiencyWhPerKm,
            double maxAcChargePowerKw,
            double maxDcChargePowerKw,
            double chargingEfficiency,
            int currentBatteryPercent,
            ConnectorType primaryConnector,
            List<ConnectorType> supportedConnectors
    ) {}
}
