package com.vidyut.land.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.dto.LandListingResponse;
import com.vidyut.land.service.LandListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/land-listings")
@RequiredArgsConstructor
public class LandListingController {

    private final LandListingService landListingService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<LandListingResponse>> createListing(
            @Valid @RequestBody LandListingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Land listing saved for review",
                landListingService.createListing(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LandListingResponse>>> getAllListings() {
        return ResponseEntity.ok(ApiResponse.success(
                landListingService.getListingsByHostUserId(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LandListingResponse>> updateListing(
            @PathVariable Long id, @Valid @RequestBody LandListingCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Property updated successfully",
                landListingService.updateListing(currentUser.getCurrentAccountId(), id, request)));
    }
}
