package com.vidyut.session.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "charging_sessions", uniqueConstraints =
        @UniqueConstraint(name = "uk_charging_session_booking", columnNames = "bookingId"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long bookingId;
    @Column(nullable = false)
    private Long stationId;
    private Long connectorId;
    private Long vehicleId;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChargingSessionStatus status = ChargingSessionStatus.ACTIVE;
    @Builder.Default
    private String paymentStatus = "DUE";
    private double powerKw;
    private double energyKwh;
    private double cost;
    private int startBatteryPercent;
    private int currentBatteryPercent;
    private int targetBatteryPercent;
    private LocalDateTime startedAt;
    private LocalDateTime estimatedCompletionAt;
    private LocalDateTime completedAt;
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
