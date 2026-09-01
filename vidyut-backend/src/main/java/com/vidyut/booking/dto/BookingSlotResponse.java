package com.vidyut.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSlotResponse {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int availableConnectors;
    private boolean available;
}
