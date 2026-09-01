package com.vidyut.autopilot.repository;

import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutopilotStopRepository extends JpaRepository<AutopilotStop, Long> {
    List<AutopilotStop> findByTripIdOrderBySequenceNumberAscIdAsc(Long tripId);
    List<AutopilotStop> findByTripIdOrderBySequenceNumberAsc(Long tripId);

    Optional<AutopilotStop> findFirstByTripIdAndStatusOrderBySequenceNumberAsc(
            Long tripId,
            AutopilotStopStatus status
    );
    List<AutopilotStop> findByStationIdAndStatus(Long stationId, AutopilotStopStatus status);
    List<AutopilotStop> findByStationIdInAndStatus(List<Long> stationIds, AutopilotStopStatus status);
}
