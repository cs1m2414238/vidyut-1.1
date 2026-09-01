package com.vidyut.autopilot.service;

import com.vidyut.admin.entity.IncidentSeverity;
import com.vidyut.admin.entity.NetworkIncident;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.autopilot.dto.AutopilotTripResponse;
import com.vidyut.autopilot.entity.AutopilotStop;
import com.vidyut.autopilot.entity.AutopilotStopStatus;
import com.vidyut.autopilot.entity.AutopilotTripStatus;
import com.vidyut.autopilot.repository.AutopilotStopRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutopilotFaultWorkflowService {

    private static final int DEMO_DOWNTIME_MINUTES = 0;

    private final AutopilotService autopilotService;
    private final com.vidyut.autopilot.repository.AutopilotTripRepository tripRepository;
    private final AutopilotStopRepository stopRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final AdminControlService adminControlService;

    @Transactional
    public AutopilotTripResponse simulateAndPropagate(Long tripId, Long userId) {
        return simulateAndPropagate(tripId, userId, "CHARGER_NOT_STARTING", null);
    }

    /**
     * Records a driver report and requests journey recovery without changing hardware or battery telemetry.
     */
    @Transactional
    public AutopilotTripResponse simulateAndPropagate(Long tripId, Long userId, String issueCategory, String userComment) {
        com.vidyut.autopilot.entity.AutopilotTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (!java.util.Objects.equals(trip.getUserId(), userId)) throw new com.vidyut.common.exception.ForbiddenException("Journey belongs to another driver");
        AutopilotStop stop = stopRepository.findFirstByTripIdAndStatusOrderBySequenceNumberAsc(tripId, AutopilotStopStatus.RESERVED)
                .orElseThrow(() -> new BadRequestException("This trip has no active charger reservation"));
        ChargingStation station = stationRepository.findById(stop.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserved charging station not found"));
        ChargingConnector connector = matchingConnector(station, stop);

        String categoryLabel = issueCategory != null ? issueCategory.replace('_', ' ') : "Charger not starting";
        String reason = "Driver reported charger issue: " + categoryLabel + " at " + station.getName() + " · " + connector.getChargerCode();

        // A driver report creates an incident for operator review, never global hardware telemetry.
        if (userComment != null && !userComment.isBlank()) reason += ": " + userComment.substring(0, Math.min(500, userComment.length()));
        AutopilotTripResponse recovered = autopilotService.simulateChargerFault(tripId, userId, stop.getId());
        Map<String, Object> impact = impact(recovered.getStatus());
        NetworkIncident incident = adminControlService.recordDetectedIncident(station, connector, IncidentSeverity.HIGH,
                reason, DEMO_DOWNTIME_MINUTES, impact);
        return autopilotService.recordOperationalPropagation(tripId, userId,
                incident.getIncidentCode(), incident.getMaintenanceTicketId());
    }

    private ChargingConnector matchingConnector(ChargingStation station, AutopilotStop stop) {
        return station.getConnectors().stream()
                .filter(connector -> connector.getType().name().equalsIgnoreCase(stop.getConnectorType()))
                .filter(connector -> stop.getConnectorId() == null || stop.getConnectorId().equals(connector.getId()))
                .min(Comparator.comparingDouble(connector -> Math.abs(connector.getPowerKw() - stop.getPowerKw())))
                .orElseThrow(() -> new BadRequestException(
                        "The reserved station has no online " + stop.getConnectorType() + " connector to simulate"));
    }

    private Map<String, Object> impact(AutopilotTripStatus status) {
        int automatic = status == AutopilotTripStatus.REROUTED ? 1 : 0;
        int approval = status == AutopilotTripStatus.REROUTE_APPROVAL_REQUIRED ? 1 : 0;
        int manual = status == AutopilotTripStatus.REPLAN_REQUIRED ? 1 : 0;
        return Map.of(
                "affectedJourneys", 1,
                "automaticReroutes", automatic,
                "driverApprovals", approval,
                "replanRequired", manual,
                "backupConnectorAvailable", false
        );
    }
}
