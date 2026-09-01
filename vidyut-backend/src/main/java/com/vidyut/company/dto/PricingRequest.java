package com.vidyut.company.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class PricingRequest {
    @DecimalMin("0.0")
    private double pricePerKwh;
    private Double timeBasedPricePerHour;
    private Double peakPricePerKwh;
    private String peakHours;
    @DecimalMin("0.0") @DecimalMax("100.0")
    private Double studentDiscountPercent;
    private Double corporatePricePerKwh;
    private boolean dynamicPricingEnabled;
    private String couponCode;
    @DecimalMin("0.0") @DecimalMax("100.0")
    private Double couponDiscountPercent;
}
