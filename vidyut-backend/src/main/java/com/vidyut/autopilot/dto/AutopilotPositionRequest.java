package com.vidyut.autopilot.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor
public class AutopilotPositionRequest {
    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;
    @NotNull @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;
    @NotNull @DecimalMin("0") @DecimalMax("100")
    private Double batteryPercent;
    @NotNull
    private LocalDateTime recordedAt;
}
