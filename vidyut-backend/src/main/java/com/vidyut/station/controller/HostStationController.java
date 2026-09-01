package com.vidyut.station.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.host.service.HostOperationsService;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.dto.StationUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/host/stations")
@RequiredArgsConstructor
public class HostStationController {
    private final HostOperationsService hostService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<StationResponse>> create(@Valid @RequestBody StationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Station created successfully",
                hostService.createStation(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StationResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success(
                hostService.stations(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StationResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody StationUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                hostService.updateStation(currentUser.getCurrentAccountId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        hostService.deleteStation(currentUser.getCurrentAccountId(), id);
        return ResponseEntity.ok(ApiResponse.success("Station deleted successfully", null));
    }
}
