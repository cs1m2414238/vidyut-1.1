package com.vidyut.company.service;

import com.vidyut.company.dto.CompanyResponse;
import com.vidyut.company.dto.CompanyProfileUpdateRequest;
import com.vidyut.company.dto.CompanySettingsRequest;
import com.vidyut.company.dto.CompanyVerificationRequest;

public interface CompanyService {
    CompanyResponse getCompanyByAccountId(Long accountId);
    CompanyResponse updateProfile(Long accountId, CompanyProfileUpdateRequest request);
    CompanyResponse submitVerification(Long accountId, CompanyVerificationRequest request);
    CompanyResponse updateSettings(Long accountId, CompanySettingsRequest request);
    String requestEmailVerification(Long accountId);
    CompanyResponse confirmEmailVerification(Long accountId, String code);
}
