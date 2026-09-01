package com.vidyut.host.dto;

import com.vidyut.account.entity.HostVerificationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HostProfileResponse {
    private Long accountId;
    private String email;
    private boolean emailVerified;
    private String displayName;
    private String phone;
    private String address;
    private String bio;
    private HostVerificationStatus verificationStatus;
    private String kycDocumentUrl;
    private String identityType;
    private String identityLast4;
    private LocalDateTime verificationRequestedAt;
    private String bankAccountHolder;
    private String bankName;
    private String bankAccountLast4;
    private String ifscCode;
    private String payoutUpi;
    private boolean bankVerified;
    private boolean emailNotifications;
    private boolean pushNotifications;
    private boolean autoAvailability;
    private double reputationScore;
}
