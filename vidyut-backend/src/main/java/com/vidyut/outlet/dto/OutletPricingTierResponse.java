package com.vidyut.outlet.dto;

import com.vidyut.outlet.entity.OutletTierEligibility;

public record OutletPricingTierResponse(Long id, String name, double ratePerKwh,
                                        OutletTierEligibility eligibility, String eligibilityNote) {}
