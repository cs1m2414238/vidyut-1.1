package com.vidyut.company.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(length = 180)
    private String legalName;
    @Column(length = 40)
    private String cinLlpin;
    @Column(length = 20)
    private String gstin;
    @Column(length = 64)
    private String panHash;
    @Column(length = 4)
    private String panLast4;
    @Column(length = 30)
    private String udyamNumber;
    @Column(length = 600)
    private String registeredAddress;
    @Column(length = 300)
    private String website;

    @Column(length = 150)
    private String representativeName;
    @Column(length = 255)
    private String representativeWorkEmail;
    @Column(length = 20)
    private String representativePhone;
    @Column(length = 100)
    private String representativeDesignation;
    @Column(length = 1000)
    private String authorizationProofUrl;

    @Column(length = 180)
    private String bankAccountHolder;
    @Column(length = 120)
    private String bankName;
    @Column(length = 4)
    private String bankAccountLast4;
    @Column(length = 20)
    private String ifscCode;
    @Column(length = 1000)
    private String cancelledChequeUrl;

    @Column(length = 1000)
    private String incorporationDocumentUrl;
    @Column(length = 1000)
    private String gstCertificateUrl;
    @Column(length = 1000)
    private String chargerCatalogueUrl;
    @Column(length = 1000)
    private String complianceDocumentUrl;

    @Builder.Default private boolean businessIdentityVerified = false;
    @Builder.Default private boolean representativeVerified = false;
    @Builder.Default private boolean bankVerified = false;
    @Builder.Default private boolean chargerDocumentsVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CompanyVerificationStatus status = CompanyVerificationStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CompanyTrustLevel trustLevel = CompanyTrustLevel.UNVERIFIED;

    @Column(length = 1200)
    private String adminReviewNote;
    @Column(length = 1200)
    private String rejectionReason;
    private Long reviewedByAdminId;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
