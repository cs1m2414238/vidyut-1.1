package com.vidyut.host.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class HostWithdrawRequest {
    @DecimalMin(value = "100.00", message = "Minimum withdrawal is ₹100")
    private double amount;
}
