package com.vidyut.vehicle.service;

import com.vidyut.vehicle.dto.VehicleCreateRequest;
import com.vidyut.vehicle.dto.VehicleResponse;
import com.vidyut.vehicle.dto.VehicleUpdateRequest;

import java.util.List;

public interface VehicleService {
    VehicleResponse addVehicle(Long userId, VehicleCreateRequest request);
    VehicleResponse getVehicleById(Long id, Long userId);
    List<VehicleResponse> getVehiclesByUserId(Long userId);
    VehicleResponse updateVehicle(Long id, Long userId, VehicleUpdateRequest request);
    void deleteVehicle(Long id);
    void deleteVehicle(Long id, Long userId);
}
