package com.vidyut.wallet.repository;

import com.vidyut.wallet.entity.VehicleWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleWalletRepository extends JpaRepository<VehicleWallet, Long> {
    List<VehicleWallet> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<VehicleWallet> findByUserIdAndVehicle_Id(Long userId, Long vehicleId);
    void deleteByUserIdAndVehicle_Id(Long userId, Long vehicleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from VehicleWallet w join fetch w.vehicle where w.userId = :userId and w.vehicle.id = :vehicleId")
    Optional<VehicleWallet> findLocked(@Param("userId") Long userId, @Param("vehicleId") Long vehicleId);
}
