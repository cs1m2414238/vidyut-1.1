package com.vidyut.marketplace.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.marketplace.dto.*;
import com.vidyut.marketplace.entity.InterestStatus;
import com.vidyut.marketplace.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/marketplace")
@RequiredArgsConstructor
public class HostMarketplaceController {
    private final MarketplaceService marketplace;
    private final CurrentUserUtil currentUser;

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<MarketplaceCompanyResponse>>> companies(@RequestParam Long propertyId) {
        return ResponseEntity.ok(ApiResponse.success(marketplace.matchingCompanies(accountId(), propertyId)));
    }

    @PostMapping("/installation-requests")
    public ResponseEntity<ApiResponse<InstallationRequestResponse>> createRequest(@Valid @RequestBody InstallationCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Installation request sent", marketplace.createRequest(accountId(), request)));
    }

    @GetMapping("/installation-requests")
    public ResponseEntity<ApiResponse<List<InstallationRequestResponse>>> requests() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.hostRequests(accountId())));
    }

    @PostMapping("/installation-requests/{id}/accept-proposal")
    public ResponseEntity<ApiResponse<InstallationRequestResponse>> acceptProposal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Proposal accepted", marketplace.acceptProposal(accountId(), id)));
    }

    @PostMapping("/installation-requests/{id}/cancel")
    public ResponseEntity<ApiResponse<InstallationRequestResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Request cancelled", marketplace.cancelRequest(accountId(), id)));
    }

    @GetMapping("/company-interests")
    public ResponseEntity<ApiResponse<List<PropertyInterestResponse>>> interests() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.hostInterests(accountId())));
    }

    @PostMapping("/company-interests/{id}/accept")
    public ResponseEntity<ApiResponse<PropertyInterestResponse>> acceptInterest(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Company interest accepted",
                marketplace.respondToInterest(accountId(), id, InterestStatus.ACCEPTED)));
    }

    @PostMapping("/company-interests/{id}/decline")
    public ResponseEntity<ApiResponse<PropertyInterestResponse>> declineInterest(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Company interest declined",
                marketplace.respondToInterest(accountId(), id, InterestStatus.DECLINED)));
    }

    private Long accountId() { return currentUser.getCurrentAccountId(); }
}
