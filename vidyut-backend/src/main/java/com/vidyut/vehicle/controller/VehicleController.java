package com.vidyut.vehicle.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.vehicle.dto.VehicleCreateRequest;
import com.vidyut.vehicle.dto.VehicleResponse;
import com.vidyut.vehicle.dto.VehicleUpdateRequest;
import com.vidyut.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ev/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService vehicleService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> addVehicle(@Valid @RequestBody VehicleCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle added successfully",
                vehicleService.addVehicle(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getVehicles() {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.getVehiclesByUserId(currentUser.getCurrentAccountId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> getVehicle(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                vehicleService.getVehicleById(id, currentUser.getCurrentAccountId())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody VehicleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vehicle data updated successfully",
                vehicleService.updateVehicle(id, currentUser.getCurrentAccountId(), request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id, currentUser.getCurrentAccountId());
        return ResponseEntity.ok(ApiResponse.success("Vehicle removed successfully", null));
    }
}
