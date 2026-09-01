package com.vidyut.booking.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistRequest {
    @NotNull private Long stationId;
    private Long vehicleId;
    private LocalDateTime preferredStartTime;
    @Min(15) @Max(720) @Builder.Default private int durationMinutes = 60;
}
