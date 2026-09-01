package com.vidyut.wallet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRechargeRuleRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    private boolean enabled;

    @DecimalMin(value = "100.0", message = "Balance threshold must be at least 100")
    @DecimalMax(value = "10000.0", message = "Balance threshold cannot exceed 10000")
    private double balanceThreshold;

    @DecimalMin(value = "100.0", message = "Recharge amount must be at least 100")
    @DecimalMax(value = "25000.0", message = "Recharge amount cannot exceed 25000")
    private double rechargeAmount;

    @NotBlank(message = "Payment method is required")
    @Size(max = 80, message = "Payment method is too long")
    private String paymentMethod;
}
