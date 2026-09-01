package com.vidyut.booking.service;

import com.vidyut.booking.dto.BookingCreateRequest;
import com.vidyut.booking.dto.BookingResponse;
import com.vidyut.booking.dto.BookingStatusUpdateRequest;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.outlet.service.OutletAccessService;
import com.vidyut.outlet.service.OutletRateDecision;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.admin.service.OperationalControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import com.vidyut.booking.dto.BookingSlotResponse;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ChargingStationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final OutletAccessService outletAccessService;
    private final NotificationService notificationService;
    private final WaitlistService waitlistService;
    private final OperationalControlService operationalControlService;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request, Long userId) {
        String idempotencyKey = request.getIdempotencyKey() == null ? null : request.getIdempotencyKey().trim();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Booking existing = bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (existing != null) return mapToResponse(existing);
        }
        ChargingStation station = stationRepository.findLockedById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found with id: " + request.getStationId()));
        operationalControlService.assertBookingAllowed(userId, station);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Booking existing = bookingRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (existing != null) return mapToResponse(existing);
        }

        if (station.getStatus() != StationStatus.ACTIVE || station.getAvailability() != StationAvailability.AVAILABLE
                || station.isEmergencyDisabled()) {
            throw new BadRequestException("This charging station is not currently available for booking");
        }

        int durationMinutes = requestedDurationMinutes(request);
        int bookingSlotMinutes = Math.max(15, station.getBookingSlotMinutes());
        if (durationMinutes % bookingSlotMinutes != 0) {
            throw new BadRequestException("Booking duration must use " + bookingSlotMinutes + "-minute slots");
        }
        int hours = Math.max(1, (int) Math.ceil(durationMinutes / 60.0));
        double powerKw = station.getConnectors().stream()
                .filter(connector -> request.getConnectorId()==null || request.getConnectorId().equals(connector.getId()))
                .filter(connector -> connector.isAvailable() && !connector.isMaintenanceMode()
                        && connector.getStatus() == ChargerStatus.ONLINE)
                .mapToDouble(connector -> connector.getPowerKw())
                .max()
                .orElseThrow(() -> new BadRequestException("This station has no available charging connector"));
        double estKwh = round(powerKw * durationMinutes / 60.0);
        OutletRateDecision rate = outletAccessService.resolveRate(userId, station.getId(), station.getPricePerKwh());
        double totalAmount = round(estKwh * rate.ratePerKwh());

        if (request.getVehicleId() != null) {
            vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        }

        LocalDateTime startTime = request.getStartTime() != null ? request.getStartTime() : LocalDateTime.now();
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes);
        int connectorCapacity = (int) station.getConnectors().stream()
                .filter(connector -> connector.isAvailable() && !connector.isMaintenanceMode()
                        && connector.getStatus() == ChargerStatus.ONLINE)
                .count();
        long overlapping = bookingRepository.countOverlapping(station.getId(), startTime, endTime,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
        if (overlapping >= connectorCapacity) {
            throw new DuplicateResourceException("The selected charging slot is full. Join the waitlist or choose another time.");
        }

        if (request.getConnectorId()!=null && bookingRepository.countConnectorOverlapping(request.getConnectorId(), startTime, endTime,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS)) > 0)
            throw new DuplicateResourceException("The exact recovery connector is already reserved for this time. Evaluate recovery again.");

        Booking booking = Booking.builder()
                .userId(userId)
                .stationId(station.getId())
                .vehicleId(request.getVehicleId())
                .connectorId(request.getConnectorId())
                .idempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey)
                .stationName(station.getName())
                .stationAddress(station.getAddress())
                .startTime(startTime)
                .endTime(endTime)
                .durationHours(hours)
                .durationMinutes(durationMinutes)
                .totalAmount(totalAmount)
                .kwhDelivered(estKwh)
                .outletId(rate.outletId())
                .outletTierName(rate.tierName())
                .appliedRatePerKwh(rate.ratePerKwh())
                .status(BookingStatus.CONFIRMED)
                .build();
        Booking saved = bookingRepository.save(booking);
        notificationService.sendNotification(userId, "Booking confirmed",
                "Your slot at " + station.getName() + " on " + startTime + " is confirmed.",
                NotificationType.BOOKING_CONFIRMED, "vidyut://booking/" + saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public BookingResponse getBookingById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        return mapToResponse(booking);
    }

    @Override
    public BookingResponse getBookingById(Long id, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
        return mapToResponse(booking);
    }

    @Override
    public List<BookingResponse> getBookingsByUserId(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public long getUnreadActiveCount(Long userId) {
        return bookingRepository.countByUserIdAndSeenFalseAndStatusIn(userId,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
    }

    @Override
    @Transactional
    public void markBookingsSeen(Long userId) {
        bookingRepository.markAllSeenByUserId(userId);
    }

    @Override
    public BookingResponse updateBookingStatus(Long id, BookingStatusUpdateRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        if (request.getStatus() != null) {
            booking.setStatus(request.getStatus());
        }
        return mapToResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        waitlistService.promoteNext(saved.getStationId());
        return mapToResponse(saved);
    }

    @Override
    public BookingResponse cancelBooking(Long id, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
        if (booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BadRequestException("This booking can no longer be cancelled");
        }
        double fee = cancellationFee(booking);
        booking.setCancellationFee(fee);
        // Bookings are estimated but not prepaid; there is no captured amount to refund here.
        booking.setRefundAmount(0);
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        waitlistService.promoteNext(saved.getStationId());
        notificationService.sendNotification(userId, "Booking cancelled",
                "Booking at " + saved.getStationName() + " was cancelled. Fee: ₹" + saved.getCancellationFee() + ".",
                NotificationType.BOOKING_CANCELLED, "vidyut://booking/" + saved.getId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public BookingResponse cancelBookingWithoutFee(Long id, Long userId, String reason) {
        Booking booking = bookingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
        if (booking.getStatus() == BookingStatus.IN_PROGRESS || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BadRequestException("An active or completed booking cannot be transferred");
        }
        booking.setCancellationFee(0);
        booking.setRefundAmount(0);
        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);
        waitlistService.promoteNext(saved.getStationId());
        notificationService.sendNotification(userId, "Booking transferred",
                reason == null ? "Your previous booking was released without a fee." : reason,
                NotificationType.STATION_FULL_DIVERSION, "vidyut://booking/" + id);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingSlotResponse> getAvailability(Long stationId, LocalDate date) {
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found with id: " + stationId));
        int slotMinutes = Math.max(15, station.getBookingSlotMinutes());
        int capacity = (int) station.getConnectors().stream()
                .filter(connector -> connector.isAvailable() && !connector.isMaintenanceMode()
                        && connector.getStatus() == ChargerStatus.ONLINE)
                .count();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<Booking> bookings = bookingRepository.findByStationIdAndStartTimeBetweenAndStatusInOrderByStartTimeAsc(
                stationId, dayStart, dayEnd,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
        LocalDateTime cursor = date.atTime(LocalTime.of(6, 0));
        LocalDateTime finalSlot = date.atTime(LocalTime.of(23, 0));
        List<BookingSlotResponse> slots = new ArrayList<>();
        while (!cursor.isAfter(finalSlot)) {
            LocalDateTime slotEnd = cursor.plusMinutes(slotMinutes);
            LocalDateTime slotStart = cursor;
            long used = bookings.stream().filter(booking -> booking.getStartTime().isBefore(slotEnd)
                    && effectiveEndTime(booking).isAfter(slotStart)).count();
            int available = Math.max(0, capacity - (int) used);
            slots.add(BookingSlotResponse.builder()
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .availableConnectors(available)
                    .available(available > 0 && slotStart.isAfter(LocalDateTime.now()))
                    .build());
            cursor = slotEnd;
        }
        return slots;
    }

    private int requestedDurationMinutes(BookingCreateRequest request) {
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            return request.getDurationMinutes();
        }
        if (request.getDurationHours() != null && request.getDurationHours() > 0) {
            return request.getDurationHours() * 60;
        }
        return 60;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private LocalDateTime effectiveEndTime(Booking booking) {
        if (booking.getEndTime() != null) return booking.getEndTime();
        int minutes = booking.getDurationMinutes() > 0 ? booking.getDurationMinutes()
                : Math.max(1, booking.getDurationHours()) * 60;
        return booking.getStartTime().plusMinutes(minutes);
    }

    private double cancellationFee(Booking booking) {
        if (booking.getStartTime() == null || booking.getStartTime().isAfter(LocalDateTime.now().plusHours(2))) {
            return 0;
        }
        return round(Math.min(booking.getTotalAmount(), booking.getTotalAmount() * 0.10));
    }

    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .connectorId(booking.getConnectorId())
                .id(booking.getId())
                .userId(booking.getUserId())
                .stationId(booking.getStationId())
                .vehicleId(booking.getVehicleId())
                .idempotencyKey(booking.getIdempotencyKey())
                .stationName(booking.getStationName())
                .stationAddress(booking.getStationAddress())
                .startTime(booking.getStartTime())
                .endTime(effectiveEndTime(booking))
                .durationHours(booking.getDurationHours())
                .durationMinutes(booking.getDurationMinutes() > 0
                        ? booking.getDurationMinutes()
                        : Math.max(1, booking.getDurationHours()) * 60)
                .totalAmount(booking.getTotalAmount())
                .kwhDelivered(booking.getKwhDelivered())
                .outletId(booking.getOutletId())
                .outletTierName(booking.getOutletTierName())
                .appliedRatePerKwh(booking.getAppliedRatePerKwh())
                .cancellationFee(booking.getCancellationFee())
                .refundAmount(booking.getRefundAmount())
                .status(booking.getStatus())
                .seen(booking.isSeen())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
