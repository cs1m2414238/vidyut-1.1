package com.vidyut.autopilot.repository;

import com.vidyut.autopilot.entity.RouteExperience;
import com.vidyut.autopilot.entity.RouteExperienceOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface RouteExperienceRepository extends JpaRepository<RouteExperience, Long> {
    List<RouteExperience> findTop30ByOriginKeyAndDestinationKeyOrderByCreatedAtDesc(String originKey, String destinationKey);
    boolean existsByTripIdAndStationIdAndOutcome(Long tripId, Long stationId, RouteExperienceOutcome outcome);
    void deleteByUserIdIn(Collection<Long> userIds);
}
