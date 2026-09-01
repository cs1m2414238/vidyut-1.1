package com.vidyut.outlet.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.outlet.dto.*;
import com.vidyut.outlet.service.OutletAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OutletController {
    private final OutletAccessService outletService;
    private final CurrentUserUtil currentUser;

    @GetMapping("/outlets/{outletId}/my-tier")
    public ResponseEntity<ApiResponse<OutletTierResponse>> myTier(@PathVariable Long outletId) {
        return ResponseEntity.ok(ApiResponse.success(
                outletService.myTier(currentUser.getCurrentAccountId(), outletId)));
    }

    @GetMapping("/outlets/{outletId}/pricing")
    public ResponseEntity<ApiResponse<List<OutletPricingTierResponse>>> pricing(@PathVariable Long outletId) {
        return ResponseEntity.ok(ApiResponse.success(outletService.pricing(outletId)));
    }

    @GetMapping("/outlets/{outletId}/my-stats")
    public ResponseEntity<ApiResponse<OutletStatsResponse>> stats(@PathVariable Long outletId) {
        return ResponseEntity.ok(ApiResponse.success(
                outletService.stats(currentUser.getCurrentAccountId(), outletId)));
    }

    @PostMapping("/users/verify-institution")
    public ResponseEntity<ApiResponse<OutletVerificationResponse>> verify(
            @Valid @RequestBody InstitutionVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Institution ID submitted",
                outletService.submitVerification(currentUser.getCurrentAccountId(),
                        request.getOutletId(), request.getDocumentUri())));
    }
}
