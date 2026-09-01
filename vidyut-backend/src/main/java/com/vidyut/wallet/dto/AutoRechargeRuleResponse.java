package com.vidyut.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRechargeRuleResponse {
    private Long id;
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private boolean enabled;
    private double balanceThreshold;
    private double rechargeAmount;
    private String paymentMethod;
    private LocalDateTime lastTriggeredAt;
    private LocalDateTime updatedAt;
}
