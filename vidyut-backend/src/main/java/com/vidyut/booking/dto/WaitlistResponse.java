package com.vidyut.booking.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponse {
    private Long id;
    private Long stationId;
    private String stationName;
    private Long vehicleId;
    private LocalDateTime preferredStartTime;
    private int durationMinutes;
    private int position;
    private String status;
    private LocalDateTime createdAt;
}
