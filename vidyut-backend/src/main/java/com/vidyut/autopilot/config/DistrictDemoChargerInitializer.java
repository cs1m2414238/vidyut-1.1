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
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Order(100)
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class DistrictDemoChargerInitializer implements ApplicationRunner {

    static final String SEED_RESOURCE = "demo/district-chargers-india.json";
    private static final double[] CCS2_POWER_LEVELS = {60, 90, 120, 150, 180};

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

        List<DistrictSeed> seeds;
        try (var input = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            seeds = objectMapper.readValue(input, new TypeReference<>() {});
        }

        Map<String, ChargingStation> existingBySeedKey = new HashMap<>();
        for (ChargingStation station : stationRepository.findAll()) {
            if (station.getDemoSeedKey() != null && !station.getDemoSeedKey().isBlank()) {
                existingBySeedKey.putIfAbsent(station.getDemoSeedKey(), station);
            }
        }

        List<ChargingStation> changed = new ArrayList<>(seeds.size());
        for (DistrictSeed seed : seeds) {
            ChargingStation station = existingBySeedKey.get(seed.key());
            if (station == null) {
                station = ChargingStation.builder()
                        .demoSeedKey(seed.key())
                        .connectors(new ArrayList<>())
                        .build();
            }
            applySeed(station, seed);
            changed.add(station);
        }
        stationRepository.saveAll(changed);
    }

    private void applySeed(ChargingStation station, DistrictSeed seed) {
        int variant = Math.floorMod(seed.key().hashCode(), CCS2_POWER_LEVELS.length);
        station.setName("Vidyut " + seed.district() + " District Demo Hub");
        station.setAddress("Synthetic district coverage point, " + seed.district() + ", " + seed.state());
        station.setCity(seed.district());
        station.setLatitude(seed.latitude());
        station.setLongitude(seed.longitude());
        station.setPricePerKwh(11.5 + variant * 0.6);
        station.setRating(4.5 + (variant % 4) * 0.1);
        station.setReviewCount(40 + variant * 19);
        station.setAmenities("Washroom, Food, Wi-Fi, 24x7 Security");
        station.setWorkingHours("Open 24 hours");
        station.setQueueCount(0);
        station.setOccupancyPercent(20);
        station.setStatus(StationStatus.ACTIVE);
        station.setAvailability(StationAvailability.AVAILABLE);
        station.setBookingSlotMinutes(30);
        station.setDemoData(true);
        station.setOwnershipType(StationOwnershipType.COMPANY_OWNED);
        station.setDemoSeedKey(seed.key());

        Map<ConnectorType, Double> desired = new EnumMap<>(ConnectorType.class);
        desired.put(ConnectorType.CCS2, CCS2_POWER_LEVELS[variant]);
        desired.put(ConnectorType.TYPE2, 22.0);
        desired.put(ConnectorType.CHADEMO, 50.0);
        desired.put(ConnectorType.GB_T, 60.0);
        desired.put(ConnectorType.TYPE1, 7.2);
        syncConnectors(station, desired);
    }

    private void syncConnectors(ChargingStation station, Map<ConnectorType, Double> desired) {
        if (station.getConnectors() == null) station.setConnectors(new ArrayList<>());
        station.getConnectors().removeIf(connector -> !desired.containsKey(connector.getType()));
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
            connector.setChargerCode("DIST-" + station.getDemoSeedKey() + "-" + entry.getKey().name());
            connector.setStatus(ChargerStatus.ONLINE);
            connector.setMaintenanceMode(false);
            connector.setHealthScore(96);
            connector.setFirmwareVersion("3.5.0-district-demo");
        }
    }

    record DistrictSeed(
            String key,
            String state,
            String district,
            double latitude,
            double longitude
    ) {}
}
