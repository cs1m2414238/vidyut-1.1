package com.vidyut.outlet.config;

import com.vidyut.outlet.entity.OutletPricingTier;
import com.vidyut.outlet.entity.OutletTierEligibility;
import com.vidyut.outlet.repository.OutletPricingTierRepository;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Order(200)
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class OutletDemoInitializer implements ApplicationRunner {

    static final String KANPUR_OUTLET_SEED_KEY = "SOI-09-164";

    private final ChargingStationRepository stationRepository;
    private final OutletPricingTierRepository tierRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ChargingStation station = stationRepository.findAll().stream()
                .filter(item -> KANPUR_OUTLET_SEED_KEY.equals(item.getDemoSeedKey()))
                .findFirst()
                .orElse(null);
        if (station == null || !station.isDemoData()) return;

        station.setOutletPartner(true);
        station.setOutletInstitutionName("Kanpur Institute Demo Campus");
        station.setOutletEmailDomains("vidyut-campus.demo");
        station.setOutletIdVerificationRequired(true);
        stationRepository.save(station);

        Map<String, OutletPricingTier> tiers = new HashMap<>();
        tierRepository.findByStationIdOrderByPriorityAsc(station.getId())
                .forEach(tier -> tiers.putIfAbsent(tier.getName(), tier));
        upsert(tiers, station.getId(), "Faculty", 7.0, OutletTierEligibility.EMAIL_DOMAIN,
                "vidyut-campus.demo", 10);
        upsert(tiers, station.getId(), "Student", 9.0, OutletTierEligibility.VERIFIED_ID,
                null, 20);
        upsert(tiers, station.getId(), "Visitor", 12.5, OutletTierEligibility.VISITOR,
                null, 100);
        tierRepository.saveAll(List.copyOf(tiers.values()));
    }

    private void upsert(Map<String, OutletPricingTier> tiers, Long stationId, String name, double rate,
                        OutletTierEligibility eligibility, String emailDomain, int priority) {
        OutletPricingTier tier = tiers.computeIfAbsent(name,
                ignored -> OutletPricingTier.builder().stationId(stationId).name(name).build());
        tier.setStationId(stationId);
        tier.setRatePerKwh(rate);
        tier.setEligibility(eligibility);
        tier.setEmailDomain(emailDomain);
        tier.setPriority(priority);
    }
}
