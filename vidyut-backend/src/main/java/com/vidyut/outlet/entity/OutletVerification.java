package com.vidyut.outlet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outlet_verifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_outlet_verification_user_station", columnNames = {"user_id", "station_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutletVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "document_uri", nullable = false, length = 1000)
    private String documentUri;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutletVerificationStatus status;

    @Column(name = "approved_tier_id")
    private Long approvedTierId;

    @Column(length = 500)
    private String reviewNote;

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
