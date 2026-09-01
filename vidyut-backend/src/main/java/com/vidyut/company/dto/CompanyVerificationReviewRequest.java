package com.vidyut.company.dto;

import com.vidyut.company.entity.CompanyTrustLevel;
import com.vidyut.company.entity.CompanyVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompanyVerificationReviewRequest(
        @NotNull CompanyVerificationStatus status,
        boolean businessIdentityVerified,
        boolean representativeVerified,
        boolean bankVerified,
        boolean chargerDocumentsVerified,
        CompanyTrustLevel trustLevel,
        @Size(max = 1200) String note,
        @Size(max = 1200) String rejectionReason
) {}
