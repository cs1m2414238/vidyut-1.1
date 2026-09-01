package com.vidyut.wallet.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopUpRequest {

    @Min(value = 10, message = "Minimum top up amount is 10")
    private double amount;

    private String paymentMethod;
}
