package com.vidyut.company.dto;

import com.vidyut.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record CompanySettlementTransactionResponse(
        Long paymentId,
        Long bookingId,
        String stationName,
        double amount,
        PaymentStatus status,
        String gatewayTransactionId,
        LocalDateTime timestamp
) {}
