package com.vidyut.agent.tool;

import com.vidyut.station.service.ChargingStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StationTool {

    private final ChargingStationService stationService;

    public int getAvailableStationCount() {
        return stationService.getAllStations().size();
    }
}
