package com.vidyut.station;

import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ChargingStationServiceTest {

    @Autowired
    private ChargingStationService stationService;

    @Test
    public void testCreateStation() {
        StationCreateRequest request = StationCreateRequest.builder()
                .name("Gomti Nagar Green Charger")
                .address("Gomti Nagar Phase 1, Lucknow")
                .city("Lucknow")
                .latitude(26.8467)
                .longitude(80.9462)
                .pricePerKwh(12.5)
                .connectorType(ConnectorType.TYPE2)
                .powerKw(7.4)
                .build();

        StationResponse response = stationService.createStation(request, 1L);
        assertNotNull(response.getId());
        assertEquals("Gomti Nagar Green Charger", response.getName());
        assertEquals(12.5, response.getPricePerKwh());
    }
}
