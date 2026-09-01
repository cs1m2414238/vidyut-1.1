package com.vidyut.station.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "charging_connectors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingConnector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorType type;

    private double powerKw;
    private boolean available;

    @Column(unique = true)
    private String chargerCode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ChargerStatus status = ChargerStatus.ONLINE;

    @Builder.Default
    private boolean maintenanceMode = false;

    @Builder.Default
    private String firmwareVersion = "1.0.0";

    @Builder.Default
    private int healthScore = 100;

    @Builder.Default
    private java.time.LocalDateTime lastHeartbeat = java.time.LocalDateTime.now();

    @Builder.Default
    private double currentPowerKw = 0;

    @Builder.Default
    private double sessionEnergyKwh = 0;

    private java.time.LocalDateTime sessionStartedAt;

    @Column(length = 120)
    private String faultCode;

    @Column(length = 500)
    private String faultReason;
    @Column(length = 60)
    private String statusSource;
    private java.time.LocalDateTime statusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    @JsonIgnore
    private ChargingStation station;
}
