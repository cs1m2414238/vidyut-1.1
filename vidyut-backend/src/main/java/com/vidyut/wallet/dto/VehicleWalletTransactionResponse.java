package com.vidyut.wallet.dto;

import com.vidyut.wallet.entity.TransactionType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWalletTransactionResponse {
    private Long id;
    private Long bookingId;
    private double amount;
    private double balanceBefore;
    private double balanceAfter;
    private TransactionType type;
    private String description;
    private String paymentMethod;
    private String paymentReference;
    private LocalDateTime timestamp;
}
