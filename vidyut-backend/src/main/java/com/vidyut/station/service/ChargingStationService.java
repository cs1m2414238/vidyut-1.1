package com.vidyut.station.service;

import com.vidyut.station.dto.*;

import java.util.List;

public interface ChargingStationService {
    StationResponse createStation(StationCreateRequest request, Long hostUserId);
    StationResponse createCompanyStation(StationCreateRequest request, Long companyAccountId,
                                         Long companyId, String companyName);
    StationResponse getStationById(Long id);
    List<StationResponse> getAllStations();
    List<StationResponse> getStationsWithinBounds(double minLat, double maxLat, double minLng, double maxLng, int limit);

    List<NearbyStationResponse> getNearbyStations(double latitude, double longitude, double radiusKm);
    List<StationResponse> searchStations(String query, String connectorType, Double latitude, Double longitude,
                                         Double radiusKm, Integer minAvailableSlots, Double maxPricePerKwh,
                                         Double minPowerKw, Boolean availableOnly);
    StationResponse updateStation(Long id, StationUpdateRequest request);
    StationResponse updateStation(Long id, Long ownerAccountId, StationUpdateRequest request);
    void deleteStation(Long id);
    void deleteStation(Long id, Long ownerAccountId);
    List<StationResponse> getStationsByOwner(Long ownerAccountId);
}
