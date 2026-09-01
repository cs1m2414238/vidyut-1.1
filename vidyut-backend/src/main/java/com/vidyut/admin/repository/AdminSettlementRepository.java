package com.vidyut.admin.repository;

import com.vidyut.admin.entity.AdminSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminSettlementRepository extends JpaRepository<AdminSettlement, Long> {
    Optional<AdminSettlement> findByPaymentId(Long paymentId);
    List<AdminSettlement> findAllByOrderByUpdatedAtDesc();
}
