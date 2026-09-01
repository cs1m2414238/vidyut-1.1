package com.vidyut.payment.dto;

import com.vidyut.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long userId;
    private Long bookingId;
    private double amount;
    private String gatewayTransactionId;
    private PaymentStatus status;
    private LocalDateTime timestamp;
}
