package com.vidyut.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_waitlist")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long stationId;
    private Long vehicleId;
    private LocalDateTime preferredStartTime;
    @Builder.Default private int durationMinutes = 60;
    @Builder.Default private String status = "WAITING";
    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
}
