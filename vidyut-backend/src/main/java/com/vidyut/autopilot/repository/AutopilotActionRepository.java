package com.vidyut.autopilot.repository;

import com.vidyut.autopilot.entity.AutopilotAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutopilotActionRepository extends JpaRepository<AutopilotAction, Long> {
    List<AutopilotAction> findByTripIdOrderBySequenceNumberAsc(Long tripId);
    long countByTripId(Long tripId);
}
