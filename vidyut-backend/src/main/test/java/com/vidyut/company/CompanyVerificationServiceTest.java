package com.vidyut.company;

import com.vidyut.account.entity.*;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.company.dto.CompanyVerificationReviewRequest;
import com.vidyut.company.dto.CompanyVerificationSubmission;
import com.vidyut.company.entity.*;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.repository.CompanyVerificationRepository;
import com.vidyut.company.service.CompanyVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class CompanyVerificationServiceTest {
    @Autowired AccountRepository accountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyVerificationRepository verificationRepository;
    @Autowired CompanyVerificationService verificationService;

    @Test
    void firstVerificationReadCreatesTheCompanyVerificationRecord() {
        Account account = accountRepository.save(Account.builder().email("first-read@test.local").passwordHash("hash")
                .accountType(AccountType.COMPANY).roles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                .enabled(true).emailVerified(true).build());
        Company company = companyRepository.save(Company.builder().account(account).companyName("First Read Grid")
                .registrationNumber("U12345UP2026PTC654321").contactName("Ravi Kumar")
                .supportEmail(account.getEmail()).active(true).verificationStatus(VerificationStatus.PENDING).build());

        var response = verificationService.getForAccount(account.getId());

        assertThat(response.status()).isEqualTo(CompanyVerificationStatus.NOT_STARTED);
        assertThat(response.marketplaceEnabled()).isFalse();
        assertThat(verificationRepository.findByCompany_Id(company.getId())).isPresent();
    }

    @Test
    void sensitiveNumbersAreReducedAndAllLayersAreRequiredBeforeMarketplaceAccess() {
        Account account = accountRepository.save(Account.builder().email("verification@test.local").passwordHash("hash")
                .accountType(AccountType.COMPANY).roles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                .enabled(true).emailVerified(false).build());
        Company company = companyRepository.save(Company.builder().account(account).companyName("Trust Grid Pvt Ltd")
                .registrationNumber("U12345UP2026PTC123456").contactName("Asha Singh")
                .supportEmail(account.getEmail()).active(true).verificationStatus(VerificationStatus.PENDING).build());

        var response = verificationService.submit(account.getId(), submission());
        CompanyVerification stored = verificationRepository.findByCompany_Id(company.getId()).orElseThrow();

        assertThat(response.status()).isEqualTo(CompanyVerificationStatus.UNDER_REVIEW);
        assertThat(response.marketplaceEnabled()).isFalse();
        assertThat(stored.getPanHash()).hasSize(64).doesNotContain("ABCDE1234F");
        assertThat(stored.getPanLast4()).isEqualTo("234F");
        assertThat(stored.getBankAccountLast4()).isEqualTo("6789");
        assertThat(stored.toString()).doesNotContain("1234567890126789");

        assertThatThrownBy(() -> verificationService.review(company.getId(), 77L,
                new CompanyVerificationReviewRequest(CompanyVerificationStatus.VERIFIED, true, true, true, true,
                        CompanyTrustLevel.VIDYUT_VERIFIED, "Reviewed", null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("email");

        account.setEmailVerified(true);
        accountRepository.save(account);
        response = verificationService.review(company.getId(), 77L,
                new CompanyVerificationReviewRequest(CompanyVerificationStatus.VERIFIED, true, true, true, true,
                        CompanyTrustLevel.VIDYUT_VERIFIED, "All evidence matched", null));
        assertThat(response.marketplaceEnabled()).isTrue();
        assertThat(response.completedLayers()).isEqualTo(4);
        assertThat(verificationService.requireMarketplaceVerified(account.getId()).getId()).isEqualTo(company.getId());
    }

    private CompanyVerificationSubmission submission() {
        return new CompanyVerificationSubmission("Trust Grid Pvt Ltd", "U12345UP2026PTC123456", "09ABCDE1234F1Z5",
                "ABCDE1234F", "UDYAM-UP-01-1234567", "12 Gomti Nagar, Lucknow", "https://trustgrid.test",
                "Asha Singh", "asha@trustgrid.test", "9876543210", "Director",
                "https://docs.test/authorization.pdf", "Trust Grid Pvt Ltd", "State Bank of India",
                "1234567890126789", "SBIN0001234", "https://docs.test/cheque.pdf",
                "https://docs.test/incorporation.pdf", "https://docs.test/gst.pdf",
                "https://docs.test/catalogue.pdf", "https://docs.test/compliance.pdf");
    }
}
