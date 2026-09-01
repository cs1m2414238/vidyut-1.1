package com.vidyut.wallet.entity;

import com.vidyut.vehicle.entity.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "vehicle_auto_recharge_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auto_recharge_user_vehicle",
                columnNames = {"user_id", "vehicle_id"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleAutoRechargeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private double balanceThreshold;

    @Column(nullable = false)
    private double rechargeAmount;

    @Column(nullable = false, length = 80)
    private String paymentMethod;

    private LocalDateTime lastTriggeredAt;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
