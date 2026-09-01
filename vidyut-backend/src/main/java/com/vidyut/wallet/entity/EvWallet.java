package com.vidyut.wallet.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ev_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private double balance;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
