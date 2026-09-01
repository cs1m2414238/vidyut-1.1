package com.vidyut.booking.repository;

import com.vidyut.booking.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<WaitlistEntry> findByStationIdAndStatusOrderByCreatedAtAsc(Long stationId, String status);
    Optional<WaitlistEntry> findByIdAndUserId(Long id, Long userId);
}
