package com.vidyut.outlet.dto;

import com.vidyut.outlet.entity.OutletVerificationStatus;

import java.util.List;

public record OutletTierResponse(Long outletId, String institutionName, String tierName, double ratePerKwh,
                                 String reason, OutletVerificationStatus verificationStatus,
                                 boolean idUploadRequired, List<OutletPricingTierResponse> pricing) {}
