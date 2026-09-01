package com.vidyut.station.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.station.dto.NearbyStationResponse;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.service.ChargingStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class ChargingStationController {
    private final ChargingStationService stationService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StationResponse>> getStationById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(stationService.getStationById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StationResponse>>> getAllStations(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(required = false, defaultValue = "250") int limit) {
        boolean anyBound = minLat != null || maxLat != null || minLng != null || maxLng != null;
        boolean allBounds = minLat != null && maxLat != null && minLng != null && maxLng != null;
        if (anyBound && !allBounds) {
            throw new com.vidyut.common.exception.BadRequestException(
                    "minLat, maxLat, minLng and maxLng must be supplied together.");
        }
        if (allBounds) {
            return ResponseEntity.ok(ApiResponse.success(
                    stationService.getStationsWithinBounds(minLat, maxLat, minLng, maxLng, limit)));
        }
        return ResponseEntity.ok(ApiResponse.success(stationService.getAllStations()));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyStationResponse>>> getNearbyStations(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "10.0") double radius) {
        return ResponseEntity.ok(ApiResponse.success(stationService.getNearbyStations(lat, lng, radius)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StationResponse>>> searchStations(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String connectorType,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false, defaultValue = "50") Double radius,
            @RequestParam(required = false, defaultValue = "0") Integer minAvailableSlots,
            @RequestParam(required = false) Double maxPricePerKwh,
            @RequestParam(required = false) Double minPowerKw,
            @RequestParam(required = false, defaultValue = "false") Boolean availableOnly) {
        return ResponseEntity.ok(ApiResponse.success(stationService.searchStations(query, connectorType, lat, lng,
                radius, minAvailableSlots, maxPricePerKwh, minPowerKw, availableOnly)));
    }
}
