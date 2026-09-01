package com.vidyut.payment.repository;

import com.vidyut.payment.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByHostUserId(Long hostUserId);
}
