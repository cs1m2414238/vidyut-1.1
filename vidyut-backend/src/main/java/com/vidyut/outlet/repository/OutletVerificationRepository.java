package com.vidyut.outlet.repository;

import com.vidyut.outlet.entity.OutletVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutletVerificationRepository extends JpaRepository<OutletVerification, Long> {
    Optional<OutletVerification> findByUserIdAndStationId(Long userId, Long stationId);
    List<OutletVerification> findByStatusOrderByUpdatedAtAsc(com.vidyut.outlet.entity.OutletVerificationStatus status);
}
