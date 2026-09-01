package com.vidyut.wallet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long walletId;
    @Column(nullable = false)
    private Long vehicleId;
    private Long bookingId;
    private double amount;
    private double balanceBefore;
    private double balanceAfter;
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    private String description;
    private String paymentMethod;
    private String paymentReference;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
