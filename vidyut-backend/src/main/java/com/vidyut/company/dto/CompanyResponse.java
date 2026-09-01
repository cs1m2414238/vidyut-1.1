package com.vidyut.company.dto;

import com.vidyut.company.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {
    private Long id;
    private String companyName;
    private String registrationNumber;
    private String contactName;
    private String supportEmail;
    private String supportPhone;
    private String gstNumber;
    private String kycDocumentUrl;
    private String businessAddress;
    private String website;
    private boolean emailVerified;
    private boolean emailNotifications;
    private boolean pushNotifications;
    private String timezone;
    private VerificationStatus verificationStatus;
    private LocalDateTime verificationRequestedAt;
    private LocalDateTime createdAt;
}
