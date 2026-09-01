package com.vidyut.autopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "autopilot_stops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutopilotStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tripId;

    @Column(nullable = false)
    private int sequenceNumber;

    @Column(nullable = false)
    private Long stationId;

    private Long bookingId;
    private Long connectorId;
    private String chargerCode;

    @Column(nullable = false)
    private String stationName;

    @Column(nullable = false)
    private String stationAddress;

    @Column(nullable = false, length = 30)
    private String connectorType;

    private double powerKw;
    private double effectivePowerKw;
    private double distanceFromOriginKm;
    private double routeOffsetKm;
    private double arrivalBatteryPercent;
    private double targetBatteryPercent;
    private int estimatedWaitMinutes;
    private int chargingMinutes;
    private int connectionMinutes;
    private double estimatedCost;

    @Builder.Default
    @Column(nullable = false)
    private boolean demoData = false;

    @Column(length = 1000)
    private String selectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutopilotStopStatus status;

    @Builder.Default
    @Column(length = 50)
    private String selectionType = "PRIMARY";

    private Long replacesStationId;
    private String replacesStationName;
    private String rerouteReason;
    private Double additionalDistanceKm;
    private Integer additionalMinutes;
    private Double additionalCost;

    private String removalReason;
    private Long replacedByStationId;
    private String replacedByStationName;
    private Integer originalStopIndex;
}
