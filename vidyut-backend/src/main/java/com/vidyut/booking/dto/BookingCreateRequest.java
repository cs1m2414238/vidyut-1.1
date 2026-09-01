package com.vidyut.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequest {

    @NotNull(message = "Station ID is required")
    private Long stationId;

    private Long vehicleId;
    private Long connectorId;

    private LocalDateTime startTime;

    @Min(value = 15, message = "Booking duration must be at least 15 minutes")
    @Max(value = 720, message = "Booking duration cannot exceed 12 hours")
    private Integer durationMinutes;

    /**
     * Backward-compatible input used by Autopilot and older clients. New clients
     * should send durationMinutes so half-hour charging slots remain precise.
     */
    @Min(value = 1, message = "Booking duration must be at least one hour")
    @Max(value = 12, message = "Booking duration cannot exceed 12 hours")
    private Integer durationHours;

    @Size(max = 80, message = "Idempotency key is too long")
    private String idempotencyKey;
}
