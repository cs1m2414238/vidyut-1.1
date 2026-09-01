package com.vidyut.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWalletTopUpRequest {
    @DecimalMin(value = "10.0", message = "Minimum top up amount is 10")
    private double amount;
    @NotBlank(message = "Payment method token is required")
    private String paymentMethod;
    @NotBlank(message = "Confirmed payment reference is required")
    private String paymentReference;
}
