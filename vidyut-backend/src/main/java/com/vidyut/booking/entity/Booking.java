package com.vidyut.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(
        name = "uk_booking_user_idempotency", columnNames = {"user_id", "idempotency_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long stationId;

    @Column(nullable = false)
    @Builder.Default
    private boolean seen = false;

    @Builder.Default
    private boolean reminderSent = false;

    private Long vehicleId;
    private Long connectorId;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    private String stationName;
    private String stationAddress;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationHours;

    /** Precise duration for newer bookings; older rows fall back to durationHours. */
    @Builder.Default
    private int durationMinutes = 0;

    private double totalAmount;
    private double kwhDelivered;

    private Long outletId;
    private String outletTierName;
    private Double appliedRatePerKwh;

    @Builder.Default
    private double cancellationFee = 0;

    @Builder.Default
    private double refundAmount = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
