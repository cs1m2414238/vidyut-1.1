package com.vidyut.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_incidents", indexes = {
        @Index(name = "idx_network_incident_status", columnList = "status,created_at"),
        @Index(name = "idx_network_incident_station", columnList = "station_id,created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkIncident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String incidentCode;
    @Column(nullable = false) private Long stationId;
    private Long connectorId;
    @Column(nullable = false, length = 180) private String stationName;
    private Long operatorCompanyId;
    @Column(length = 180) private String operatorCompanyName;
    private Long hostAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IncidentSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private IncidentStatus status;
    @Column(length = 120) private String faultCode;
    @Column(nullable = false, length = 1500) private String description;
    @Builder.Default private int affectedBookings = 0;
    @Builder.Default private int usersRerouted = 0;
    @Builder.Default private int approvalsRequired = 0;
    @Builder.Default private int manualInterventions = 0;
    @Builder.Default private int estimatedDowntimeMinutes = 0;
    private Long maintenanceTicketId;
    @Column(length = 1500) private String resolutionNote;
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
