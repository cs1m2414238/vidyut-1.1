package com.vidyut.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "host_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostProfile {
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(length = 500)
    private String bio;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private HostVerificationStatus verificationStatus = HostVerificationStatus.PENDING;

    @Column(length = 1000)
    private String kycDocumentUrl;

    @Column(length = 30)
    private String identityType;

    @Column(length = 4)
    private String identityLast4;

    private LocalDateTime verificationRequestedAt;

    @Column(length = 150)
    private String bankAccountHolder;

    @Column(length = 120)
    private String bankName;

    @Column(length = 8)
    private String bankAccountLast4;

    @Column(length = 20)
    private String ifscCode;

    @Column(length = 120)
    private String payoutUpi;

    @Builder.Default
    private boolean bankVerified = false;

    @Builder.Default
    private boolean emailNotifications = true;

    @Builder.Default
    private boolean pushNotifications = true;

    @Builder.Default
    private boolean autoAvailability = false;

    @Builder.Default
    private double reputationScore = 5.0;

    private String emailVerificationCodeHash;
    private LocalDateTime emailVerificationExpiresAt;
}
