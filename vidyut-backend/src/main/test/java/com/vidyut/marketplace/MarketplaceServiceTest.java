package com.vidyut.marketplace;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.entity.HostVerificationStatus;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.repository.CompanyVerificationRepository;
import com.vidyut.company.entity.CompanyVerification;
import com.vidyut.company.entity.CompanyVerificationStatus;
import com.vidyut.company.entity.CompanyTrustLevel;
import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.entity.OwnershipType;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import com.vidyut.land.service.LandListingService;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.marketplace.dto.*;
import com.vidyut.marketplace.entity.BusinessModel;
import com.vidyut.marketplace.entity.ChargerCurrentType;
import com.vidyut.marketplace.entity.InstallationStatus;
import com.vidyut.marketplace.entity.InterestStatus;
import com.vidyut.marketplace.entity.ProductApprovalStatus;
import com.vidyut.marketplace.repository.ChargerProductRepository;
import com.vidyut.marketplace.service.MarketplaceService;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MarketplaceServiceTest {
    @Autowired MarketplaceService marketplace;
    @Autowired LandListingService landService;
    @Autowired AccountRepository accountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired ChargingStationRepository stationRepository;
    @Autowired HostProfileRepository hostProfileRepository;
    @Autowired CompanyVerificationRepository verificationRepository;
    @Autowired LandListingRepository landRepository;
    @Autowired ChargerProductRepository productRepository;

    @Test
    void hostAndCompanyCanCompleteInstallationFromPropertyMatchToLiveStation() {
        Account host = accountRepository.save(Account.builder()
                .email("market-host@test.local").passwordHash("hash")
                .accountType(AccountType.INDIVIDUAL).roles(new HashSet<>(Set.of(AccountRole.ROLE_HOST)))
                .emailVerified(true).enabled(true).build());
        Account companyAccount = accountRepository.save(Account.builder()
                .email("market-company@test.local").passwordHash("hash")
                .accountType(AccountType.COMPANY).roles(new HashSet<>(Set.of(AccountRole.ROLE_COMPANY)))
                .emailVerified(true).enabled(true).build());
        Company company = companyRepository.save(Company.builder()
                .account(companyAccount).companyName("ChargeWorks India")
                .registrationNumber("CIN-MARKET-001").contactName("Operations Admin")
                .supportEmail(companyAccount.getEmail()).verificationStatus(VerificationStatus.VERIFIED)
                .active(true).build());
        hostProfileRepository.save(HostProfile.builder().account(host).displayName("Verified Host")
                .verified(true).verificationStatus(HostVerificationStatus.VERIFIED)
                .kycDocumentUrl("https://docs.test/host-kyc.pdf").build());
        verificationRepository.save(CompanyVerification.builder().company(company)
                .businessIdentityVerified(true).representativeVerified(true).bankVerified(true)
                .chargerDocumentsVerified(true).status(CompanyVerificationStatus.VERIFIED)
                .trustLevel(CompanyTrustLevel.VIDYUT_VERIFIED).build());

        LandListingCreateRequest propertyInput = LandListingCreateRequest.builder()
                .title("Hazratganj Commercial Parking").address("12 MG Road, Hazratganj")
                .city("Lucknow").state("Uttar Pradesh").pincode("226001")
                .latitude(26.8500).longitude(80.9500).propertyType(PropertyType.COMMERCIAL_PARKING)
                .availableParkingBays(6).powerPhase(PowerPhase.THREE_PHASE).availableLoadKw(120.0)
                .ownershipType(OwnershipType.OWNED).preferredConnectorType("CCS2").preferredPowerKw(60.0)
                .pricePerKwh(18.0).ownershipDocumentUrl("https://docs.test/land-title.pdf").discoverable(true).build();
        var property = landService.createListing(host.getId(), propertyInput);
        var approvedProperty = landRepository.findById(property.getId()).orElseThrow();
        approvedProperty.setStatus(LandListingStatus.APPROVED);
        approvedProperty.setDiscoverable(true);
        landRepository.save(approvedProperty);

        var product = marketplace.saveProduct(companyAccount.getId(), null, new ChargerProductRequest(
                "CW Fast 60", "ChargeWorks", ChargerCurrentType.DC, ConnectorType.CCS2,
                60, 450_000, 90_000, 36, true, "BIS", "60 kW commercial charger", null,
                Set.of(BusinessModel.PURCHASE, BusinessModel.REVENUE_SHARE), true,
                "https://docs.test/cw-fast-60-compliance.pdf"));
        var approvedProduct = productRepository.findById(product.id()).orElseThrow();
        approvedProduct.setApprovalStatus(ProductApprovalStatus.APPROVED);
        approvedProduct.setActive(true);
        productRepository.save(approvedProduct);

        assertThat(marketplace.matchingCompanies(host.getId(), property.getId()))
                .singleElement().satisfies(match -> {
                    assertThat(match.id()).isEqualTo(company.getId());
                    assertThat(match.products()).extracting(ChargerProductResponse::id).containsExactly(product.id());
                });
        assertThat(marketplace.matchingOpportunities(companyAccount.getId()))
                .extracting(PropertyOpportunityResponse::id).contains(property.getId());

        var interest = marketplace.expressInterest(companyAccount.getId(), property.getId(),
                new PropertyInterestRequest("We can survey this site and install a 60 kW CCS2 charger."));
        assertThat(interest.status()).isEqualTo(InterestStatus.PENDING);
        interest = marketplace.respondToInterest(host.getId(), interest.id(), InterestStatus.ACCEPTED);
        assertThat(interest.status()).isEqualTo(InterestStatus.ACCEPTED);

        var request = marketplace.createRequest(host.getId(), new InstallationCreateRequest(
                property.getId(), company.getId(), product.id(), 2, BusinessModel.PURCHASE,
                1_200_000.0, LocalDate.now().plusDays(30), "Transformer and gated access are available."));
        assertThat(request.status()).isEqualTo(InstallationStatus.REQUESTED);

        request = move(request, companyAccount.getId(), InstallationStatus.UNDER_REVIEW, null);
        request = move(request, companyAccount.getId(), InstallationStatus.SITE_SURVEY_REQUESTED, null);
        request = move(request, companyAccount.getId(), InstallationStatus.SITE_SURVEY_SCHEDULED, LocalDate.now().plusDays(2));
        request = move(request, companyAccount.getId(), InstallationStatus.SURVEY_COMPLETED, null);
        request = marketplace.sendProposal(companyAccount.getId(), request.id(), new ProposalRequest(
                900_000, 180_000, null, null, null, LocalDate.now().plusDays(14), 18,
                "Includes equipment, installation and commissioning."));
        assertThat(request.status()).isEqualTo(InstallationStatus.PROPOSAL_SENT);

        request = marketplace.acceptProposal(host.getId(), request.id());
        request = move(request, companyAccount.getId(), InstallationStatus.INSTALLATION_SCHEDULED, LocalDate.now().plusDays(7));
        request = move(request, companyAccount.getId(), InstallationStatus.INSTALLING, null);
        request = move(request, companyAccount.getId(), InstallationStatus.INSTALLED, null);
        request = move(request, companyAccount.getId(), InstallationStatus.COMMISSIONED, null);
        assertThat(request.stationId()).isNotNull();
        request = move(request, companyAccount.getId(), InstallationStatus.LIVE, null);

        var station = stationRepository.findById(request.stationId()).orElseThrow();
        assertThat(request.status()).isEqualTo(InstallationStatus.LIVE);
        assertThat(station.getStatus()).isEqualTo(StationStatus.ACTIVE);
        assertThat(station.getHostUserId()).isEqualTo(host.getId());
        assertThat(station.getSupplierCompanyId()).isEqualTo(company.getId());
        assertThat(station.getSourceInstallationRequestId()).isEqualTo(request.id());
        assertThat(station.getConnectors()).hasSize(2)
                .allMatch(connector -> connector.isAvailable() && connector.getStatus() == ChargerStatus.ONLINE);
        assertThat(request.history()).extracting(StatusHistoryResponse::status)
                .containsExactly(InstallationStatus.REQUESTED, InstallationStatus.UNDER_REVIEW,
                        InstallationStatus.SITE_SURVEY_REQUESTED, InstallationStatus.SITE_SURVEY_SCHEDULED,
                        InstallationStatus.SURVEY_COMPLETED, InstallationStatus.PROPOSAL_SENT,
                        InstallationStatus.ACCEPTED, InstallationStatus.INSTALLATION_SCHEDULED,
                        InstallationStatus.INSTALLING, InstallationStatus.INSTALLED,
                        InstallationStatus.COMMISSIONED, InstallationStatus.LIVE);
    }

    private InstallationRequestResponse move(InstallationRequestResponse request, Long companyAccountId,
            InstallationStatus status, LocalDate date) {
        return marketplace.updateStatus(companyAccountId, request.id(),
                new InstallationStatusUpdateRequest(status, status.name(), date));
    }
}
