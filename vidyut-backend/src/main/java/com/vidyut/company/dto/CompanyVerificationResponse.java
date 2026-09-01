package com.vidyut.company.dto;

import com.vidyut.company.entity.CompanyTrustLevel;
import com.vidyut.company.entity.CompanyVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CompanyVerificationResponse(
        Long id,
        Long companyId,
        String legalName,
        String cinLlpin,
        String gstin,
        String panLast4,
        String udyamNumber,
        String registeredAddress,
        String website,
        String representativeName,
        String representativeWorkEmail,
        String representativePhone,
        String representativeDesignation,
        String authorizationProofUrl,
        String bankAccountHolder,
        String bankName,
        String bankAccountLast4,
        String ifscCode,
        String cancelledChequeUrl,
        String incorporationDocumentUrl,
        String gstCertificateUrl,
        String chargerCatalogueUrl,
        String complianceDocumentUrl,
        boolean emailVerified,
        boolean businessIdentityVerified,
        boolean representativeVerified,
        boolean bankVerified,
        boolean chargerDocumentsVerified,
        CompanyVerificationStatus status,
        CompanyTrustLevel trustLevel,
        boolean marketplaceEnabled,
        int completedLayers,
        List<String> missingRequirements,
        String adminReviewNote,
        String rejectionReason,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {}
