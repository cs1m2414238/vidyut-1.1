package com.vidyut.company.service;

import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.dto.CompanyResponse;
import com.vidyut.company.dto.CompanyProfileUpdateRequest;
import com.vidyut.company.dto.CompanySettingsRequest;
import com.vidyut.company.dto.CompanyVerificationRequest;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.email.service.EmailService;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Override
    public CompanyResponse getCompanyByAccountId(Long accountId) {
        return mapToResponse(findCompany(accountId));
    }

    @Override
    @Transactional
    public CompanyResponse updateProfile(Long accountId, CompanyProfileUpdateRequest request) {
        Company company = findCompany(accountId);
        company.setCompanyName(request.getCompanyName().trim());
        company.setContactName(request.getContactName().trim());
        company.setSupportEmail(request.getSupportEmail());
        company.setSupportPhone(request.getSupportPhone());
        company.setBusinessAddress(request.getBusinessAddress());
        company.setWebsite(request.getWebsite());
        return mapToResponse(companyRepository.save(company));
    }

    @Override
    @Transactional
    public CompanyResponse submitVerification(Long accountId, CompanyVerificationRequest request) {
        Company company = findCompany(accountId);
        company.setGstNumber(request.getGstNumber().trim().toUpperCase());
        company.setKycDocumentUrl(request.getKycDocumentUrl().trim());
        company.setBusinessAddress(request.getBusinessAddress().trim());
        company.setVerificationStatus(VerificationStatus.PENDING);
        company.setVerificationRequestedAt(LocalDateTime.now());
        return mapToResponse(companyRepository.save(company));
    }

    @Override
    @Transactional
    public CompanyResponse updateSettings(Long accountId, CompanySettingsRequest request) {
        Company company = findCompany(accountId);
        company.setEmailNotifications(request.isEmailNotifications());
        company.setPushNotifications(request.isPushNotifications());
        company.setTimezone(request.getTimezone().trim());
        return mapToResponse(companyRepository.save(company));
    }

    @Override
    @Transactional
    public String requestEmailVerification(Long accountId) {
        Company company = findCompany(accountId);
        if (company.getAccount().isEmailVerified()) return "Email is already verified";
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        company.setEmailVerificationCodeHash(hash(code));
        company.setEmailVerificationExpiresAt(LocalDateTime.now().plusMinutes(15));
        companyRepository.save(company);

        emailService.sendVerificationCode(
                company.getAccount().getEmail(),
                "Company",
                code);

        notificationService.sendNotification(accountId, "Company email verification",
                "A verification code was sent to your email. It expires in 15 minutes.",
                NotificationType.SYSTEM_ALERT);
        return "Verification code sent to " + company.getAccount().getEmail();
    }

    @Override
    @Transactional
    public CompanyResponse confirmEmailVerification(Long accountId, String code) {
        Company company = findCompany(accountId);
        if (company.getAccount().isEmailVerified()) return mapToResponse(company);
        if (company.getEmailVerificationExpiresAt() == null
                || company.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now())
                || !hash(code).equals(company.getEmailVerificationCodeHash())) {
            throw new BadRequestException("Verification code is invalid or expired");
        }
        company.getAccount().setEmailVerified(true);
        accountRepository.save(company.getAccount());
        company.setEmailVerificationCodeHash(null);
        company.setEmailVerificationExpiresAt(null);
        return mapToResponse(companyRepository.save(company));
    }

    private Company findCompany(Long accountId) {
        return companyRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company profile not found for this account"));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to secure verification code", exception);
        }
    }

    private CompanyResponse mapToResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .companyName(company.getCompanyName())
                .registrationNumber(company.getRegistrationNumber())
                .contactName(company.getContactName())
                .supportEmail(company.getSupportEmail())
                .supportPhone(company.getSupportPhone())
                .gstNumber(company.getGstNumber())
                .kycDocumentUrl(company.getKycDocumentUrl())
                .businessAddress(company.getBusinessAddress())
                .website(company.getWebsite())
                .emailVerified(company.getAccount().isEmailVerified())
                .emailNotifications(company.isEmailNotifications())
                .pushNotifications(company.isPushNotifications())
                .timezone(company.getTimezone())
                .verificationStatus(company.getVerificationStatus())
                .verificationRequestedAt(company.getVerificationRequestedAt())
                .createdAt(company.getCreatedAt())
                .build();
    }
}
