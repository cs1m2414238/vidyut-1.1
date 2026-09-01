package com.vidyut.outlet.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "outlet_pricing_tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutletPricingTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "rate_per_kwh", nullable = false)
    private double ratePerKwh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutletTierEligibility eligibility;

    @Column(length = 255)
    private String emailDomain;

    @Builder.Default
    @Column(nullable = false)
    private int priority = 100;
}
