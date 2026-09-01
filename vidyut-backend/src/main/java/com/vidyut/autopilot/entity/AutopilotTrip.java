package com.vidyut.autopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "autopilot_trips",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_autopilot_user_idempotency",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 1200)
    private String goal;

    @Column(nullable = false, length = 160)
    private String origin;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(length = 20)
    private String arrivalDeadline;
    private LocalDateTime arrivalDeadlineAt;

    @Column(nullable = false, length = 30)
    private String optimizeFor;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private TripPurpose tripPurpose = TripPurpose.GENERAL;

    @Column(length = 1000)
    private String memorySummary;

    @Builder.Default
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'ASK_BEFORE_ACTIONS'")
    private String autonomyMode = "ASK_BEFORE_ACTIONS";

    private double startingBatteryPercent;
    private double currentBatteryPercent;
    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime positionRecordedAt;
    @Column(length = 40)
    private String positionSource;
    private double distanceTravelledKm;
    private int elapsedDriveMinutes;
    private double routeStartDistanceKm;
    @Column(columnDefinition = "text")
    private String navigationRouteJson;
    @Column(columnDefinition = "text")
    private String recoveryJson;
    private double minimumArrivalBatteryPercent;
    private double maximumChargingBudget;
    private double totalDistanceKm;
    private double baseRouteDistanceKm;
    private double chargingDetourDistanceKm;
    private int estimatedDriveMinutes;
    private int baseDriveMinutes;
    private int chargingDetourMinutes;
    private int estimatedChargingMinutes;
    private int estimatedQueueMinutes;
    private int connectionOverheadMinutes;
    private int totalDurationMinutes;
    private double estimatedChargingCost;
    private double estimatedArrivalBatteryPercent;
    private int feasibleAlternativesCompared;

    @Column(length = 1000)
    private String optimizationSummary;

    @Column(length = 40)
    private String routeEngine;
    private Long activeStationId;
    private Long activeBookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AutopilotTripStatus status;

    @Column(length = 500)
    private String paymentMessage;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
