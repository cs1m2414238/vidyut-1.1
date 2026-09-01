package com.vidyut.booking.dto;

import com.vidyut.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long connectorId;
    private Long id;
    private Long userId;
    private Long stationId;
    private Long vehicleId;
    private String idempotencyKey;
    private String stationName;
    private String stationAddress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationHours;
    private int durationMinutes;
    private double totalAmount;
    private double kwhDelivered;
    private Long outletId;
    private String outletTierName;
    private Double appliedRatePerKwh;
    private double cancellationFee;
    private double refundAmount;
    private BookingStatus status;
    private boolean seen;
    private LocalDateTime createdAt;
}
