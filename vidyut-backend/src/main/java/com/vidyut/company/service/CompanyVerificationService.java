package com.vidyut.company.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.company.dto.CompanyVerificationResponse;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import com.vidyut.company.dto.CompanyVerificationSubmission;
import com.vidyut.company.entity.*;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.repository.CompanyVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyVerificationService {
    private final CompanyRepository companyRepository;
    private final CompanyVerificationRepository verificationRepository;

    @Transactional
    public CompanyVerificationResponse getForAccount(Long accountId) {
        return toResponse(ensure(companyByAccount(accountId)));
    }

    public List<CompanyVerificationResponse> reviewQueue() {
        return verificationRepository.findByStatusInOrderBySubmittedAtAsc(List.of(
                CompanyVerificationStatus.DOCUMENTS_SUBMITTED,
                CompanyVerificationStatus.UNDER_REVIEW,
                CompanyVerificationStatus.NEEDS_INFORMATION,
                CompanyVerificationStatus.ESCALATED
        )).stream().map(this::toResponse).toList();
    }

    public List<CompanyVerificationResponse> reviewHistory() {
        return verificationRepository.findAll().stream()
                .filter(item -> item.getStatus() == CompanyVerificationStatus.VERIFIED
                        || item.getStatus() == CompanyVerificationStatus.REJECTED
                        || item.getStatus() == CompanyVerificationStatus.SUSPENDED)
                .sorted(java.util.Comparator.comparing(CompanyVerification::getReviewedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toResponse).toList();
    }

    @Transactional
    public CompanyVerificationResponse submit(Long accountId, CompanyVerificationSubmission input) {
        Company company = companyByAccount(accountId);
        CompanyVerification verification = ensure(company);
        if (verification.getStatus() == CompanyVerificationStatus.SUSPENDED) {
            throw new BadRequestException("This company is suspended. Contact Vidyut support before resubmitting.");
        }

        verification.setLegalName(clean(input.legalName()));
        verification.setCinLlpin(clean(input.cinLlpin()).toUpperCase());
        verification.setGstin(clean(input.gstin()).toUpperCase());
        verification.setPanHash(hash(input.pan().toUpperCase()));
        verification.setPanLast4(input.pan().substring(input.pan().length() - 4).toUpperCase());
        verification.setUdyamNumber(clean(input.udyamNumber()));
        verification.setRegisteredAddress(clean(input.registeredAddress()));
        verification.setWebsite(clean(input.website()));
        verification.setRepresentativeName(clean(input.representativeName()));
        verification.setRepresentativeWorkEmail(clean(input.representativeWorkEmail()).toLowerCase());
        verification.setRepresentativePhone(input.representativePhone());
        verification.setRepresentativeDesignation(clean(input.representativeDesignation()));
        verification.setAuthorizationProofUrl(clean(input.authorizationProofUrl()));
        verification.setBankAccountHolder(clean(input.bankAccountHolder()));
        verification.setBankName(clean(input.bankName()));
        verification.setBankAccountLast4(input.bankAccountNumber().substring(input.bankAccountNumber().length() - 4));
        verification.setIfscCode(input.ifscCode().toUpperCase());
        verification.setCancelledChequeUrl(clean(input.cancelledChequeUrl()));
        verification.setIncorporationDocumentUrl(clean(input.incorporationDocumentUrl()));
        verification.setGstCertificateUrl(clean(input.gstCertificateUrl()));
        verification.setChargerCatalogueUrl(clean(input.chargerCatalogueUrl()));
        verification.setComplianceDocumentUrl(clean(input.complianceDocumentUrl()));
        verification.setBusinessIdentityVerified(false);
        verification.setRepresentativeVerified(false);
        verification.setBankVerified(false);
        verification.setChargerDocumentsVerified(false);
        verification.setStatus(CompanyVerificationStatus.UNDER_REVIEW);
        verification.setTrustLevel(CompanyTrustLevel.UNVERIFIED);
        verification.setSubmittedAt(LocalDateTime.now());
        verification.setReviewedAt(null);
        verification.setReviewedByAdminId(null);
        verification.setAdminReviewNote(null);
        verification.setRejectionReason(null);

        company.setCompanyName(verification.getLegalName());
        company.setRegistrationNumber(verification.getCinLlpin());
        company.setGstNumber(verification.getGstin());
        company.setBusinessAddress(verification.getRegisteredAddress());
        company.setWebsite(verification.getWebsite());
        company.setKycDocumentUrl(verification.getIncorporationDocumentUrl());
        company.setVerificationStatus(VerificationStatus.PENDING);
        company.setVerificationRequestedAt(verification.getSubmittedAt());
        companyRepository.save(company);
        return toResponse(verificationRepository.save(verification));
    }

    @Transactional
    public CompanyVerificationResponse review(Long companyId, Long adminId, CompanyVerificationReviewRequest input) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + companyId));
        CompanyVerification verification = ensure(company);
        verification.setBusinessIdentityVerified(input.businessIdentityVerified());
        verification.setRepresentativeVerified(input.representativeVerified());
        verification.setBankVerified(input.bankVerified());
        verification.setChargerDocumentsVerified(input.chargerDocumentsVerified());
        verification.setAdminReviewNote(clean(input.note()));
        verification.setRejectionReason(clean(input.rejectionReason()));
        verification.setReviewedAt(LocalDateTime.now());
        verification.setReviewedByAdminId(adminId);

        if (input.status() == CompanyVerificationStatus.VERIFIED && !allLayersVerified(verification)) {
            throw new BadRequestException("Verify company email and all four verification layers before approval");
        }
        if (input.status() == CompanyVerificationStatus.REJECTED && verification.getRejectionReason() == null) {
            throw new BadRequestException("A rejection reason is required");
        }
        verification.setStatus(input.status());
        if (input.status() == CompanyVerificationStatus.VERIFIED) {
            CompanyTrustLevel requested = input.trustLevel();
            verification.setTrustLevel(requested == null || requested == CompanyTrustLevel.UNVERIFIED
                    ? CompanyTrustLevel.VIDYUT_VERIFIED : requested);
            company.setVerificationStatus(VerificationStatus.VERIFIED);
            company.setActive(true);
        } else {
            verification.setTrustLevel(input.trustLevel() == null ? CompanyTrustLevel.UNVERIFIED : input.trustLevel());
            company.setVerificationStatus(input.status() == CompanyVerificationStatus.REJECTED
                    || input.status() == CompanyVerificationStatus.SUSPENDED
                    ? VerificationStatus.REJECTED : VerificationStatus.PENDING);
            if (input.status() == CompanyVerificationStatus.SUSPENDED) company.setActive(false);
        }
        companyRepository.save(company);
        return toResponse(verificationRepository.save(verification));
    }

    public boolean isMarketplaceVerified(Company company) {
        if (!company.isActive() || !company.getAccount().isEmailVerified()) return false;
        var stored = verificationRepository.findByCompany_Id(company.getId());
        if (stored.isEmpty()) {
            // Backward-compatible only for pre-migration records. V5 backfills every PostgreSQL company.
            return company.getVerificationStatus() == VerificationStatus.VERIFIED;
        }
        return stored.filter(this::allLayersVerified)
                .filter(v -> v.getStatus() == CompanyVerificationStatus.VERIFIED)
                .filter(v -> v.getTrustLevel() == CompanyTrustLevel.VIDYUT_VERIFIED
                        || v.getTrustLevel() == CompanyTrustLevel.TRUSTED_PARTNER)
                .isPresent();
    }

    public Company requireMarketplaceVerified(Long accountId) {
        Company company = companyByAccount(accountId);
        if (!company.getAccount().isEmailVerified()) {
            throw new ForbiddenException("Company email must be verified before using protected operations");
        }
        if (!company.isActive()) {
            throw new ForbiddenException("Company account is disabled");
        }
        if (!isMarketplaceVerified(company)) {
            throw new ForbiddenException("Vidyut verification is required before listing products, contacting Hosts or sending proposals");
        }
        return company;
    }

    @Transactional
    public CompanyVerification ensure(Company company) {
        return verificationRepository.findByCompany_Id(company.getId()).orElseGet(() -> {
            boolean legacyVerified = company.getVerificationStatus() == VerificationStatus.VERIFIED;
            return verificationRepository.save(CompanyVerification.builder()
                    .company(company)
                    .legalName(company.getCompanyName())
                    .cinLlpin(company.getRegistrationNumber())
                    .gstin(company.getGstNumber())
                    .registeredAddress(company.getBusinessAddress())
                    .website(company.getWebsite())
                    .incorporationDocumentUrl(company.getKycDocumentUrl())
                    .businessIdentityVerified(legacyVerified)
                    .representativeVerified(legacyVerified)
                    .bankVerified(legacyVerified)
                    .chargerDocumentsVerified(legacyVerified)
                    .status(legacyVerified ? CompanyVerificationStatus.VERIFIED : CompanyVerificationStatus.NOT_STARTED)
                    .trustLevel(legacyVerified ? CompanyTrustLevel.VIDYUT_VERIFIED : CompanyTrustLevel.UNVERIFIED)
                    .build());
        });
    }

    private Company companyByAccount(Long accountId) {
        return companyRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company profile not found for this account"));
    }

    private boolean allLayersVerified(CompanyVerification verification) {
        return verification.getCompany().getAccount().isEmailVerified()
                && verification.isBusinessIdentityVerified()
                && verification.isRepresentativeVerified()
                && verification.isBankVerified()
                && verification.isChargerDocumentsVerified();
    }

    private CompanyVerificationResponse toResponse(CompanyVerification v) {
        List<String> missing = new ArrayList<>();
        if (!v.getCompany().getAccount().isEmailVerified()) missing.add("Company email verification");
        if (!v.isBusinessIdentityVerified()) missing.add("Business identity review");
        if (!v.isRepresentativeVerified()) missing.add("Representative authorization review");
        if (!v.isBankVerified()) missing.add("Bank account review");
        if (!v.isChargerDocumentsVerified()) missing.add("Charger and compliance document review");
        int layers = (v.isBusinessIdentityVerified() ? 1 : 0) + (v.isRepresentativeVerified() ? 1 : 0)
                + (v.isBankVerified() ? 1 : 0) + (v.isChargerDocumentsVerified() ? 1 : 0);
        return new CompanyVerificationResponse(v.getId(), v.getCompany().getId(), v.getLegalName(), v.getCinLlpin(),
                v.getGstin(), v.getPanLast4(), v.getUdyamNumber(), v.getRegisteredAddress(), v.getWebsite(),
                v.getRepresentativeName(), v.getRepresentativeWorkEmail(), v.getRepresentativePhone(),
                v.getRepresentativeDesignation(), v.getAuthorizationProofUrl(), v.getBankAccountHolder(), v.getBankName(),
                v.getBankAccountLast4(), v.getIfscCode(), v.getCancelledChequeUrl(), v.getIncorporationDocumentUrl(),
                v.getGstCertificateUrl(), v.getChargerCatalogueUrl(), v.getComplianceDocumentUrl(),
                v.getCompany().getAccount().isEmailVerified(), v.isBusinessIdentityVerified(), v.isRepresentativeVerified(),
                v.isBankVerified(), v.isChargerDocumentsVerified(), v.getStatus(), v.getTrustLevel(),
                isMarketplaceVerified(v.getCompany()), layers, missing, v.getAdminReviewNote(), v.getRejectionReason(),
                v.getSubmittedAt(), v.getReviewedAt(), v.getUpdatedAt());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect PAN", exception);
        }
    }
}
