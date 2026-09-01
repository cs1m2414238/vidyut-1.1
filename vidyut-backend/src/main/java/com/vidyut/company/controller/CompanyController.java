package com.vidyut.company.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.company.dto.CompanyResponse;
import com.vidyut.company.dto.CompanyProfileUpdateRequest;
import com.vidyut.company.dto.CompanySettingsRequest;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationSubmission;
import com.vidyut.company.dto.EmailVerificationRequest;
import com.vidyut.company.service.CompanyService;
import com.vidyut.company.service.CompanyVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyService companyService;
    private final CompanyVerificationService verificationService;
    private final CurrentUserUtil currentUser;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMyCompany() {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.getCompanyByAccountId(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateProfile(
            @Valid @RequestBody CompanyProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company profile updated",
                companyService.updateProfile(currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/verification")
    public ResponseEntity<ApiResponse<CompanyVerificationResponse>> submitVerification(
            @Valid @RequestBody CompanyVerificationSubmission request) {
        return ResponseEntity.ok(ApiResponse.success("Business verification submitted",
                verificationService.submit(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping("/verification")
    public ResponseEntity<ApiResponse<CompanyVerificationResponse>> getVerification() {
        return ResponseEntity.ok(ApiResponse.success(
                verificationService.getForAccount(currentUser.getCurrentAccountId())));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<CompanyResponse>> updateSettings(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company settings updated",
                companyService.updateSettings(currentUser.getCurrentAccountId(), request)));
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<ApiResponse<String>> requestEmailVerification() {
        return ResponseEntity.ok(ApiResponse.success(
                companyService.requestEmailVerification(currentUser.getCurrentAccountId())));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<ApiResponse<CompanyResponse>> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email verified",
                companyService.confirmEmailVerification(currentUser.getCurrentAccountId(), request.getCode())));
    }
}
