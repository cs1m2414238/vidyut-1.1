package com.vidyut.outlet;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.outlet.entity.OutletPricingTier;
import com.vidyut.outlet.entity.OutletTierEligibility;
import com.vidyut.outlet.repository.OutletPricingTierRepository;
import com.vidyut.outlet.service.OutletAccessService;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OutletAccessServiceTest {
    @Autowired private OutletAccessService outletAccessService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ChargingStationRepository stationRepository;
    @Autowired private OutletPricingTierRepository tierRepository;

    @Test
    void appliesEmailMemberRateBeforeIdVerificationAndFallsBackToVisitor() {
        ChargingStation station = stationRepository.save(ChargingStation.builder()
                .name("PSIT Outlet Demo").address("Kanpur").city("Kanpur")
                .latitude(26.45).longitude(80.33).pricePerKwh(9)
                .status(StationStatus.ACTIVE).availability(StationAvailability.AVAILABLE)
                .outletPartner(true).outletInstitutionName("PSIT")
                .outletEmailDomains("psit.ac.in").outletIdVerificationRequired(true)
                .build());
        tierRepository.save(OutletPricingTier.builder().stationId(station.getId()).name("Faculty")
                .ratePerKwh(4).eligibility(OutletTierEligibility.EMAIL_DOMAIN)
                .emailDomain("psit.ac.in").priority(10).build());
        tierRepository.save(OutletPricingTier.builder().stationId(station.getId()).name("Student")
                .ratePerKwh(6).eligibility(OutletTierEligibility.VERIFIED_ID).priority(20).build());
        tierRepository.save(OutletPricingTier.builder().stationId(station.getId()).name("Visitor")
                .ratePerKwh(9).eligibility(OutletTierEligibility.VISITOR).priority(100).build());

        Account faculty = account("faculty@psit.ac.in");
        Account visitor = account("driver@example.com");

        assertThat(outletAccessService.resolveRate(faculty.getId(), station.getId(), 9).ratePerKwh())
                .isEqualTo(4);
        assertThat(outletAccessService.resolveRate(visitor.getId(), station.getId(), 9).ratePerKwh())
                .isEqualTo(9);
    }

    private Account account(String email) {
        return accountRepository.save(Account.builder().email(email).passwordHash("test")
                .accountType(AccountType.INDIVIDUAL)
                .roles(new HashSet<>(Set.of(AccountRole.ROLE_EV_USER)))
                .emailVerified(true).enabled(true).build());
    }
}
