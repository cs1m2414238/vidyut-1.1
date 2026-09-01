package com.vidyut.autopilot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "route_experiences", indexes = {
        @Index(name = "idx_route_experience_lookup", columnList = "origin_key,destination_key,created_at"),
        @Index(name = "idx_route_experience_station", columnList = "station_id,outcome")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteExperience {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long tripId;
    private Long stationId;

    @Column(nullable = false, length = 160)
    private String origin;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(nullable = false, length = 120)
    private String originKey;

    @Column(nullable = false, length = 120)
    private String destinationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RouteExperienceOutcome outcome;

    @Column(length = 1200)
    private String detail;

    private Integer rating;
    private Integer delayMinutes;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
