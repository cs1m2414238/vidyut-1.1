package com.vidyut.booking.service;

import com.vidyut.booking.dto.*;
import com.vidyut.booking.entity.WaitlistEntry;
import com.vidyut.booking.repository.WaitlistEntryRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {
    private final WaitlistEntryRepository repository;
    private final ChargingStationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Override @Transactional
    public WaitlistResponse join(Long userId, WaitlistRequest request) {
        ChargingStation station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found"));
        if (request.getVehicleId() != null) vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
        WaitlistEntry entry = repository.save(WaitlistEntry.builder()
                .userId(userId).stationId(station.getId()).vehicleId(request.getVehicleId())
                .preferredStartTime(request.getPreferredStartTime()).durationMinutes(request.getDurationMinutes()).build());
        return map(entry, station);
    }

    @Override @Transactional(readOnly = true)
    public List<WaitlistResponse> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(entry -> map(entry,
                stationRepository.findById(entry.getStationId()).orElse(null))).toList();
    }

    @Override @Transactional
    public WaitlistResponse cancel(Long userId, Long id) {
        WaitlistEntry entry = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found"));
        entry.setStatus("CANCELLED");
        return map(repository.save(entry), stationRepository.findById(entry.getStationId()).orElse(null));
    }

    @Override
    @Transactional
    public void promoteNext(Long stationId) {
        List<WaitlistEntry> queue = repository.findByStationIdAndStatusOrderByCreatedAtAsc(stationId, "WAITING");
        if (queue.isEmpty()) return;
        WaitlistEntry entry = queue.get(0);
        entry.setStatus("AVAILABLE");
        repository.save(entry);
        String stationName = stationRepository.findById(stationId).map(ChargingStation::getName)
                .orElse("your selected station");
        notificationService.sendNotification(entry.getUserId(), "A charging slot is available",
                "A slot opened at " + stationName + ". Book it before another driver takes it.",
                NotificationType.WAITLIST_AVAILABLE, "vidyut://station/" + stationId);
    }

    private WaitlistResponse map(WaitlistEntry entry, ChargingStation station) {
        List<WaitlistEntry> queue = repository.findByStationIdAndStatusOrderByCreatedAtAsc(entry.getStationId(), "WAITING");
        int index = queue.stream().map(WaitlistEntry::getId).toList().indexOf(entry.getId());
        return WaitlistResponse.builder().id(entry.getId()).stationId(entry.getStationId())
                .stationName(station == null ? "Charging station" : station.getName()).vehicleId(entry.getVehicleId())
                .preferredStartTime(entry.getPreferredStartTime()).durationMinutes(entry.getDurationMinutes())
                .position(index < 0 ? 0 : index + 1).status(entry.getStatus()).createdAt(entry.getCreatedAt()).build();
    }
}
