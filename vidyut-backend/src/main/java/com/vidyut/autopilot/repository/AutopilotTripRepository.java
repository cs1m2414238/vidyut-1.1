package com.vidyut.autopilot.repository;

import com.vidyut.autopilot.entity.AutopilotTrip;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface AutopilotTripRepository extends JpaRepository<AutopilotTrip, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from AutopilotTrip t where t.id = :id and t.userId = :userId")
    Optional<AutopilotTrip> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("userId") Long userId);
    Optional<AutopilotTrip> findByIdAndUserId(Long id, Long userId);
    Optional<AutopilotTrip> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Optional<AutopilotTrip> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<AutopilotTrip> findFirstByUserIdAndStatusInAndUpdatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            Collection<AutopilotTripStatus> statuses,
            LocalDateTime updatedAfter
    );
    java.util.List<AutopilotTrip> findByUserIdAndStatusIn(
            Long userId,
            Collection<AutopilotTripStatus> statuses
    );
}
