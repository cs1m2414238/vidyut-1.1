package com.vidyut.marketplace.dto;

import java.time.LocalDate;

public record ProposalResponse(
        Long id, double equipmentTotal, double installationTotal, Double monthlyLease,
        Double hostRevenueSharePercent, Double companyRevenueSharePercent,
        LocalDate validUntil, int estimatedInstallationDays, String terms
) {}
