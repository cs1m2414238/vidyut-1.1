package com.vidyut.host.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "host_reviews", indexes = {
        @Index(name = "idx_host_review_host", columnList = "hostAccountId"),
        @Index(name = "idx_host_review_station", columnList = "stationId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hostAccountId;

    @Column(nullable = false)
    private Long stationId;

    private Long bookingId;

    @Column(nullable = false)
    private Long customerAccountId;

    @Column(nullable = false, length = 150)
    private String customerName;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, length = 1500)
    private String comment;

    @Column(length = 1500)
    private String hostReply;

    @Builder.Default
    private boolean reported = false;

    @Column(length = 500)
    private String reportReason;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
