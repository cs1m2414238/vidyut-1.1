package com.vidyut.host.dto;

import com.vidyut.booking.entity.BookingStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HostBookingResponse {
    private Long id;
    private Long stationId;
    private String stationName;
    private Long customerAccountId;
    private String customerName;
    private String customerEmail;
    private LocalDateTime startTime;
    private int durationHours;
    private double totalAmount;
    private double kwhDelivered;
    private BookingStatus status;
}
