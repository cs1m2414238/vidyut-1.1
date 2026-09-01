package com.vidyut.session.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SessionSocUpdateRequest {
    @Min(0)
    @Max(100)
    private int batteryPercent;
    private boolean simulated;
}
