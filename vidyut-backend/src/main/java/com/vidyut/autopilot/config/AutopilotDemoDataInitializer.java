package com.vidyut.autopilot.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.entity.StationOwnershipType;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class AutopilotDemoDataInitializer implements ApplicationRunner {

    private static final String SEED_RESOURCE = "demo/chargers-india.json";
    private final ChargingStationRepository stationRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${demo.seed.enabled:false}")
    private boolean demoSeedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (demoSeedEnabled) {
            return;
        }

        List<DemoStation> seeds;
        try (var input = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            seeds = objectMapper.readValue(input, new TypeReference<>() {});
        }

        Map<String, ChargingStation> existingByName = stationRepository.findAll().stream()
                .collect(Collectors.toMap(ChargingStation::getName, Function.identity(), (first, ignored) -> first));

        for (DemoStation seed : seeds) {
            if (stationRepository.existsByDemoSeedKey(seed.key())) {
                continue;
            }
            ChargingStation existing = existingByName.get(seed.name());
            if (existing == null) {
                stationRepository.save(toEntity(seed));
            } else if (existing.getHostUserId() == null && existing.getSupplierCompanyId() == null) {
                updateSeededStation(existing, seed);
                stationRepository.save(existing);
            }
        }
    }

    private ChargingStation toEntity(DemoStation seed) {
        ChargingStation station = ChargingStation.builder()
                .name(seed.name())
                .address(seed.address())
                .city(seed.city())
                .latitude(seed.latitude())
                .longitude(seed.longitude())
                .pricePerKwh(seed.pricePerKwh() > 0 ? seed.pricePerKwh() : 12.5)
                .rating(4.7)
                .reviewCount(120)
                .amenities("Cafe, Washroom, Wi-Fi, 24x7 Security")
                .workingHours("Open 24 hours")
                .queueCount(0)
                .occupancyPercent(25)
                .status(StationStatus.ACTIVE)
                .availability(StationAvailability.AVAILABLE)
                .bookingSlotMinutes(30)
                .demoData(true)
                .ownershipType(StationOwnershipType.COMPANY_OWNED)
                .connectors(new ArrayList<>())
                .build();
        syncConnectors(station, seed);
        return station;
    }

    private void updateSeededStation(ChargingStation station, DemoStation seed) {
        station.setAddress(seed.address());
        station.setCity(seed.city());
        station.setLatitude(seed.latitude());
        station.setLongitude(seed.longitude());
        station.setDemoData(true);
        station.setOwnershipType(StationOwnershipType.COMPANY_OWNED);
        station.setStatus(StationStatus.ACTIVE);
        station.setAvailability(StationAvailability.AVAILABLE);
        syncConnectors(station, seed);
    }

    private void syncConnectors(ChargingStation station, DemoStation seed) {
        if (station.getConnectors() == null) station.setConnectors(new ArrayList<>());
        Map<ConnectorType, Double> desired = new EnumMap<>(ConnectorType.class);
        desired.put(ConnectorType.CCS2, seed.powerKw() > 0 ? seed.powerKw() : 120);
        desired.put(ConnectorType.TYPE2, 22.0);
        desired.put(ConnectorType.CHADEMO, 50.0);
        desired.put(ConnectorType.GB_T, 60.0);
        desired.put(ConnectorType.TYPE1, 7.2);
        Map<ConnectorType, ChargingConnector> existing = new EnumMap<>(ConnectorType.class);
        station.getConnectors().forEach(connector -> existing.putIfAbsent(connector.getType(), connector));
        for (Map.Entry<ConnectorType, Double> entry : desired.entrySet()) {
            ChargingConnector connector = existing.get(entry.getKey());
            if (connector == null) {
                connector = ChargingConnector.builder()
                        .station(station)
                        .type(entry.getKey())
                        .build();
                station.getConnectors().add(connector);
            }
            connector.setStation(station);
            connector.setPowerKw(entry.getValue());
            connector.setAvailable(true);
            connector.setChargerCode("DEMO-" + seed.key() + "-" + entry.getKey().name());
            connector.setStatus(ChargerStatus.ONLINE);
            connector.setMaintenanceMode(false);
            connector.setHealthScore(96);
            connector.setFirmwareVersion("3.5.0-demo");
        }
    }

    private record DemoStation(
            String key,
            String name,
            String address,
            String city,
            double latitude,
            double longitude,
            double pricePerKwh,
            double powerKw
    ) {}
}
