package com.vidyut.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    private Long vehicleId;

    private double amount;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private String description;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
