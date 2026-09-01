package com.vidyut.session.repository;

import com.vidyut.session.entity.ChargingSession;
import com.vidyut.session.entity.ChargingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {
    Optional<ChargingSession> findByIdAndUserId(Long id, Long userId);
    Optional<ChargingSession> findByBookingIdAndUserId(Long bookingId, Long userId);
    List<ChargingSession> findByUserIdAndStatusOrderByStartedAtDesc(Long userId, ChargingSessionStatus status);
    List<ChargingSession> findByStationIdInAndStatusOrderByStartedAtDesc(
            Collection<Long> stationIds, ChargingSessionStatus status);
    Optional<ChargingSession> findFirstByConnectorIdAndStatusOrderByStartedAtDesc(
            Long connectorId, ChargingSessionStatus status);
}
