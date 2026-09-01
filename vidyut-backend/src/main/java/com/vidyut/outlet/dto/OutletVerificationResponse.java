package com.vidyut.outlet.dto;

import com.vidyut.outlet.entity.OutletVerificationStatus;

public record OutletVerificationResponse(Long id, Long outletId, OutletVerificationStatus status,
                                         Long approvedTierId, String reviewNote) {}
