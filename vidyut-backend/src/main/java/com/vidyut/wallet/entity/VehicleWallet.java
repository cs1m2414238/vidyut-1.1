package com.vidyut.wallet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vidyut.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_wallets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vehicle_wallet_vehicle", columnNames = "vehicle_id"),
        @UniqueConstraint(name = "uk_vehicle_wallet_tag", columnNames = "tag_uid")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    @JsonIgnore
    private Vehicle vehicle;

    @Column(name = "tag_uid", nullable = false, length = 64)
    private String tagUid;

    @Builder.Default
    private double balance = 0;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
