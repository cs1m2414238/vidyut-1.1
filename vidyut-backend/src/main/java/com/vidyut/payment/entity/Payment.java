package com.vidyut.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long bookingId;

    private double amount;
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
