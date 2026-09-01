package com.vidyut.booking.repository;

import com.vidyut.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.vidyut.booking.entity.BookingStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Booking> findByIdAndUserId(Long id, Long userId);
    Optional<Booking> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Booking> findByStationId(Long stationId);
    @Query("select count(b) from Booking b where b.connectorId = :connectorId " +
            "and b.status in :statuses and b.startTime < :endTime and b.endTime > :startTime")
    long countConnectorOverlapping(@Param("connectorId") Long connectorId,
                          @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
                          @Param("statuses") Collection<BookingStatus> statuses);
    List<Booking> findByStationIdInOrderByStartTimeDesc(List<Long> stationIds);
    long countByUserIdAndSeenFalseAndStatusIn(Long userId, Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.status = :status and b.reminderSent = false " +
            "and b.startTime between :from and :to order by b.startTime asc")
    List<Booking> findPendingReminders(@Param("status") BookingStatus status,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("select count(b) from Booking b where b.stationId = :stationId " +
            "and b.status in :statuses and b.startTime < :endTime and b.endTime > :startTime")
    long countOverlapping(@Param("stationId") Long stationId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findByStationIdAndStartTimeBetweenAndStatusInOrderByStartTimeAsc(
            Long stationId, LocalDateTime from, LocalDateTime to, Collection<BookingStatus> statuses);

    @Modifying
    @Query("update Booking b set b.seen = true where b.userId = :userId and b.seen = false")
    int markAllSeenByUserId(@Param("userId") Long userId);
}
