package com.vidyut.company.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_maintenance_tickets", indexes = {
        @Index(name = "idx_company_maintenance_company_updated", columnList = "company_id,updated_at"),
        @Index(name = "idx_company_maintenance_charger", columnList = "charger_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyMaintenanceTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "charger_id", nullable = false)
    private Long chargerId;

    @Column(nullable = false, length = 120)
    private String chargerCode;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(nullable = false, length = 180)
    private String stationName;

    @Column(length = 120)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private MaintenanceTicketStatus status = MaintenanceTicketStatus.OPEN;

    @Column(nullable = false, length = 1500)
    private String issue;

    @Column(length = 150)
    private String assignedTo;

    @Column(length = 2000)
    private String resolutionNote;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;
}
