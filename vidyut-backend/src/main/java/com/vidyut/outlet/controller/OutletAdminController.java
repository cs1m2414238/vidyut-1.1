package com.vidyut.outlet.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.outlet.dto.OutletVerificationResponse;
import com.vidyut.outlet.dto.OutletVerificationReviewRequest;
import com.vidyut.outlet.service.OutletAccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/outlets")
@RequiredArgsConstructor
public class OutletAdminController {
    private final OutletAccessService outletService;

    @GetMapping("/verifications/pending")
    public ResponseEntity<ApiResponse<List<OutletVerificationResponse>>> pending() {
        return ResponseEntity.ok(ApiResponse.success(outletService.pendingVerifications()));
    }

    @PostMapping("/verifications/{verificationId}/review")
    public ResponseEntity<ApiResponse<OutletVerificationResponse>> review(
            @PathVariable Long verificationId, @Valid @RequestBody OutletVerificationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Outlet verification reviewed", outletService.review(
                verificationId, request.getApproved(), request.getTierId(), request.getNote())));
    }
}
