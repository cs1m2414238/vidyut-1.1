package com.vidyut.company.dto;

import java.util.List;

public record CompanySettlementResponse(
        double collected,
        double pending,
        double refunded,
        double netRevenue,
        long successfulTransactions,
        List<CompanySettlementTransactionResponse> recentTransactions
) {}
