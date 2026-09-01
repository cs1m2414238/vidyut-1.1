package com.vidyut.wallet.repository;

import com.vidyut.wallet.entity.EvWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface EvWalletRepository extends JpaRepository<EvWallet, Long> {
    Optional<EvWallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from EvWallet wallet where wallet.userId = :userId")
    Optional<EvWallet> findLockedByUserId(@Param("userId") Long userId);
}
