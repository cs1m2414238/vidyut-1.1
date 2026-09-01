package com.vidyut.marketplace.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ProposalRequest(
        @DecimalMin("0") double equipmentTotal,
        @DecimalMin("0") double installationTotal,
        @DecimalMin("0") Double monthlyLease,
        @DecimalMin("0") @DecimalMax("100") Double hostRevenueSharePercent,
        @DecimalMin("0") @DecimalMax("100") Double companyRevenueSharePercent,
        @NotNull LocalDate validUntil,
        @Min(1) @Max(365) int estimatedInstallationDays,
        @Size(max = 2000) String terms
) {}
