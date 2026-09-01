package com.vidyut.marketplace.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.marketplace.dto.*;
import com.vidyut.marketplace.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/company/marketplace")
@RequiredArgsConstructor
public class CompanyMarketplaceController {
    private final MarketplaceService marketplace;
    private final CurrentUserUtil currentUser;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ChargerProductResponse>>> products() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.companyProducts(accountId())));
    }

    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ChargerProductResponse>> createProduct(@Valid @RequestBody ChargerProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Charger product added", marketplace.saveProduct(accountId(), null, request)));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ChargerProductResponse>> updateProduct(@PathVariable Long id, @Valid @RequestBody ChargerProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Charger product updated", marketplace.saveProduct(accountId(), id, request)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        marketplace.deleteProduct(accountId(), id);
        return ResponseEntity.ok(ApiResponse.success("Charger product archived", null));
    }

    @GetMapping("/opportunities")
    public ResponseEntity<ApiResponse<List<PropertyOpportunityResponse>>> opportunities() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.matchingOpportunities(accountId())));
    }

    @PostMapping("/opportunities/{propertyId}/interest")
    public ResponseEntity<ApiResponse<PropertyInterestResponse>> interest(@PathVariable Long propertyId,
            @Valid @RequestBody PropertyInterestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Interest sent to Host", marketplace.expressInterest(accountId(), propertyId, request)));
    }

    @PostMapping("/opportunities/{propertyId}/save")
    public ResponseEntity<ApiResponse<PropertyInterestResponse>> save(@PathVariable Long propertyId) {
        return ResponseEntity.ok(ApiResponse.success("Property saved privately", marketplace.saveOpportunity(accountId(), propertyId)));
    }

    @GetMapping("/interests")
    public ResponseEntity<ApiResponse<List<PropertyInterestResponse>>> interests() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.companyInterests(accountId())));
    }

    @GetMapping("/installation-requests")
    public ResponseEntity<ApiResponse<List<InstallationRequestResponse>>> requests() {
        return ResponseEntity.ok(ApiResponse.success(marketplace.companyRequests(accountId())));
    }

    @PostMapping("/installation-requests/{id}/proposal")
    public ResponseEntity<ApiResponse<InstallationRequestResponse>> proposal(@PathVariable Long id,
            @Valid @RequestBody ProposalRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Proposal sent", marketplace.sendProposal(accountId(), id, request)));
    }

    @PatchMapping("/installation-requests/{id}/status")
    public ResponseEntity<ApiResponse<InstallationRequestResponse>> status(@PathVariable Long id,
            @Valid @RequestBody InstallationStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Installation status updated", marketplace.updateStatus(accountId(), id, request)));
    }

    private Long accountId() { return currentUser.getCurrentAccountId(); }
}
