package com.vidyut.wallet.repository;

import com.vidyut.wallet.entity.VehicleAutoRechargeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleAutoRechargeRuleRepository extends JpaRepository<VehicleAutoRechargeRule, Long> {
    List<VehicleAutoRechargeRule> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<VehicleAutoRechargeRule> findByUserIdAndVehicle_Id(Long userId, Long vehicleId);
    void deleteByUserIdAndVehicle_Id(Long userId, Long vehicleId);
}
