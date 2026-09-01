package com.vidyut.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyVerificationSubmission(
        @NotBlank @Size(max = 180) String legalName,
        @NotBlank @Size(max = 40) String cinLlpin,
        @NotBlank @Pattern(regexp = "^[0-9A-Z]{15}$") String gstin,
        @NotBlank @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$") String pan,
        @Size(max = 30) String udyamNumber,
        @NotBlank @Size(max = 600) String registeredAddress,
        @Size(max = 300) String website,
        @NotBlank @Size(max = 150) String representativeName,
        @NotBlank @Email String representativeWorkEmail,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$") String representativePhone,
        @NotBlank @Size(max = 100) String representativeDesignation,
        @NotBlank @Size(max = 1000) String authorizationProofUrl,
        @NotBlank @Size(max = 180) String bankAccountHolder,
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Pattern(regexp = "^[0-9]{9,18}$") String bankAccountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$") String ifscCode,
        @NotBlank @Size(max = 1000) String cancelledChequeUrl,
        @NotBlank @Size(max = 1000) String incorporationDocumentUrl,
        @NotBlank @Size(max = 1000) String gstCertificateUrl,
        @NotBlank @Size(max = 1000) String chargerCatalogueUrl,
        @NotBlank @Size(max = 1000) String complianceDocumentUrl
) {}
