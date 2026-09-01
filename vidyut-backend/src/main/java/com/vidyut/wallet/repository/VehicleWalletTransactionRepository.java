package com.vidyut.wallet.repository;

import com.vidyut.wallet.entity.VehicleWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleWalletTransactionRepository extends JpaRepository<VehicleWalletTransaction, Long> {
    List<VehicleWalletTransaction> findTop50ByWalletIdOrderByTimestampDesc(Long walletId);
}
