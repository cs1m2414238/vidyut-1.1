package com.vidyut.session.service;

import com.vidyut.booking.entity.*;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.session.dto.ChargingSessionResponse;
import com.vidyut.session.entity.*;
import com.vidyut.session.repository.ChargingSessionRepository;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingStationRepository;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.entity.VehicleTelemetrySource;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.service.VehicleWalletService;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChargingSessionServiceImpl implements ChargingSessionService {
    private final ChargingSessionRepository sessionRepository;
    private final BookingRepository bookingRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleWalletService vehicleWalletService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ChargingSessionResponse start(Long userId, Long bookingId) {
        ChargingSession existing = sessionRepository.findByBookingIdAndUserId(bookingId, userId).orElse(null);
        if (existing != null) return map(refresh(existing));
        Booking booking = ownedBooking(userId, bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new BadRequestException("Only a confirmed booking can start charging");
        }
        ChargingStation station = stationRepository.findById(booking.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found"));
        ChargingConnector connector = station.getConnectors().stream()
                .filter(candidate -> candidate.getStatus() == ChargerStatus.ONLINE && candidate.isAvailable()
                        && !candidate.isMaintenanceMode())
                .max(java.util.Comparator.comparingDouble(ChargingConnector::getPowerKw))
                .orElseThrow(() -> new BadRequestException("No connector is ready to start charging"));
        Vehicle vehicle = booking.getVehicleId() == null ? null : vehicleRepository.findByIdAndUserId(
                booking.getVehicleId(), userId).orElse(null);
        int startBattery = vehicle == null || vehicle.getBatteryPercent() == null ? 20 : vehicle.getBatteryPercent();
        if (startBattery < 0) startBattery = 20;
        double powerKw = effectiveBatteryPower(connector, vehicle, startBattery);
        int durationMinutes = booking.getDurationMinutes() > 0 ? booking.getDurationMinutes()
                : Math.max(1, booking.getDurationHours()) * 60;
        LocalDateTime now = LocalDateTime.now();
        ChargingSession session = ChargingSession.builder()
                .userId(userId).bookingId(bookingId).stationId(station.getId()).connectorId(connector.getId())
                .vehicleId(booking.getVehicleId())
                .powerKw(powerKw).startBatteryPercent(startBattery).currentBatteryPercent(startBattery)
                .targetBatteryPercent(80).startedAt(now).estimatedCompletionAt(now.plusMinutes(durationMinutes)).build();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        bookingRepository.save(booking);
        markConnectorCharging(connector, session);
        updateVehicleCharging(session, station, true);
        ChargingSession saved = sessionRepository.save(session);
        notificationService.sendNotification(userId, "Charging started",
                "Charging started at " + station.getName() + ". Live battery and cost are now available.",
                NotificationType.CHARGING_STARTED, "vidyut://session/" + saved.getId());
        return map(saved);
    }

    @Override
    @Transactional
    public ChargingSessionResponse get(Long userId, Long sessionId) {
        return map(refresh(ownedSession(userId, sessionId)));
    }

    @Override
    @Transactional
    public List<ChargingSessionResponse> getActive(Long userId) {
        return sessionRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, ChargingSessionStatus.ACTIVE)
                .stream().map(this::refresh).map(this::map).toList();
    }

    @Override
    @Transactional
    public ChargingSessionResponse stop(Long userId, Long sessionId) {
        ChargingSession session = refresh(ownedSession(userId, sessionId));
        if (session.getStatus() == ChargingSessionStatus.COMPLETED) return map(session);
        complete(session);
        return map(session);
    }

    @Override
    @Transactional
    public ChargingSessionResponse pay(Long userId, Long sessionId) {
        ChargingSession session = refresh(ownedSession(userId, sessionId));
        if (session.getStatus() != ChargingSessionStatus.COMPLETED) {
            throw new BadRequestException("Stop the charging session before payment");
        }
        if ("PAID".equals(session.getPaymentStatus())) return map(session);
        if (session.getVehicleId() == null) throw new BadRequestException("This booking has no vehicle wallet");
        vehicleWalletService.deduct(userId, session.getVehicleId(), session.getCost(), session.getBookingId(),
                "Charging session #" + session.getId());
        session.setPaymentStatus("PAID");
        session.setUpdatedAt(LocalDateTime.now());
        syncConnectorTelemetry(session);
        ChargingSession saved = sessionRepository.save(session);
        notificationService.sendNotification(userId, "Charging payment complete",
                "₹" + saved.getCost() + " was paid from the vehicle wallet.",
                NotificationType.PAYMENT_RECEIVED, "vidyut://session/" + saved.getId());
        return map(saved);
    }

    @Override
    @Transactional
    public ChargingSessionResponse updateSoc(Long userId, Long sessionId, int batteryPercent, boolean simulated) {
        ChargingSession session = ownedSession(userId, sessionId);
        if (session.getStatus() == ChargingSessionStatus.COMPLETED) return map(session);
        session.setCurrentBatteryPercent(Math.max(session.getStartBatteryPercent(), batteryPercent));
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getVehicleId() != null) {
            vehicleRepository.findByIdAndUserId(session.getVehicleId(), userId).ifPresent(vehicle -> {
                vehicle.setBatteryPercent(session.getCurrentBatteryPercent());
                vehicle.setCharging(true);
                vehicle.setTelemetrySource(simulated
                        ? VehicleTelemetrySource.BLUETOOTH_DEMO : VehicleTelemetrySource.BLUETOOTH);
                vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);
                double capacity = parseCapacity(vehicle.getBatteryCapacity());
                double remainingKwh = capacity * Math.max(0,
                        session.getTargetBatteryPercent() - session.getCurrentBatteryPercent()) / 100.0;
                long remainingMinutes = Math.max(1,
                        (long) Math.ceil(remainingKwh / Math.max(1, session.getPowerKw()) * 60));
                session.setEstimatedCompletionAt(LocalDateTime.now().plusMinutes(remainingMinutes));
            });
        }
        if (session.getCurrentBatteryPercent() >= session.getTargetBatteryPercent()) {
            complete(session);
        } else {
            syncConnectorTelemetry(session);
            sessionRepository.save(session);
        }
        return map(session);
    }

    @Override
    @Transactional
    public ChargingSessionResponse control(Long userId, Long sessionId, String action) {
        ChargingSession session = ownedSession(userId, sessionId);
        String normalized = action == null ? "" : action.trim().toUpperCase();
        if ("STOP".equals(normalized)) return stop(userId, sessionId);
        if (!"START".equals(normalized)) throw new BadRequestException("Bluetooth action must be START or STOP");
        if (session.getStatus() == ChargingSessionStatus.COMPLETED) {
            throw new BadRequestException("A completed session cannot be restarted");
        }
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getVehicleId() != null) {
            vehicleRepository.findByIdAndUserId(session.getVehicleId(), userId).ifPresent(vehicle -> {
                if (!vehicle.isBtSessionControlEnabled()) {
                    throw new BadRequestException("Bluetooth session control is disabled for this vehicle");
                }
                vehicle.setCharging(true);
                vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);
            });
        }
        syncConnectorTelemetry(session);
        return map(sessionRepository.save(session));
    }

    private ChargingSession refresh(ChargingSession session) {
        if (session.getStatus() == ChargingSessionStatus.COMPLETED) return session;
        long seconds = Math.max(0, Duration.between(session.getStartedAt(), LocalDateTime.now()).toSeconds());
        double energy = round(session.getPowerKw() * seconds / 3600.0);
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        double maxEnergy = Math.max(booking.getKwhDelivered(), 0.1);
        session.setEnergyKwh(Math.min(energy, maxEnergy));
        ChargingStation station = stationRepository.findById(session.getStationId()).orElseThrow();
        double rate = booking.getAppliedRatePerKwh() == null
                ? station.getPricePerKwh() : booking.getAppliedRatePerKwh();
        session.setCost(round(session.getEnergyKwh() * rate));
        if (session.getVehicleId() != null) {
            vehicleRepository.findByIdAndUserId(session.getVehicleId(), session.getUserId()).ifPresent(vehicle -> {
                double capacity = parseCapacity(vehicle.getBatteryCapacity());
                int added = capacity <= 0 ? 0 : (int) Math.round(session.getEnergyKwh() / capacity * 100);
                session.setCurrentBatteryPercent(Math.min(session.getTargetBatteryPercent(),
                        session.getStartBatteryPercent() + added));
            });
        }
        session.setUpdatedAt(LocalDateTime.now());
        if (!LocalDateTime.now().isBefore(session.getEstimatedCompletionAt())
                || session.getCurrentBatteryPercent() >= session.getTargetBatteryPercent()) {
            complete(session);
        } else {
            syncConnectorTelemetry(session);
        }
        return sessionRepository.save(session);
    }

    private void complete(ChargingSession session) {
        session.setStatus(ChargingSessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setKwhDelivered(session.getEnergyKwh());
        booking.setTotalAmount(session.getCost());
        bookingRepository.save(booking);
        ChargingStation station = stationRepository.findById(session.getStationId()).orElseThrow();
        updateVehicleCharging(session, station, false);
        releaseConnector(session);
        NotificationType type = session.getVehicleId() != null && vehicleRepository.findById(session.getVehicleId())
                .map(vehicle -> vehicle.getTelemetrySource() == VehicleTelemetrySource.BLUETOOTH
                        || vehicle.getTelemetrySource() == VehicleTelemetrySource.BLUETOOTH_DEMO)
                .orElse(false) ? NotificationType.BT_CHARGE_COMPLETED : NotificationType.CHARGING_COMPLETED;
        notificationService.sendNotification(session.getUserId(), "Charging complete",
                "Session complete at " + station.getName() + ": " + session.getEnergyKwh()
                        + " kWh delivered for ₹" + session.getCost() + ".",
                type, "vidyut://session/" + session.getId());
    }

    private void updateVehicleCharging(ChargingSession session, ChargingStation station, boolean charging) {
        if (session.getVehicleId() == null) return;
        vehicleRepository.findByIdAndUserId(session.getVehicleId(), session.getUserId()).ifPresent(vehicle -> {
            vehicle.setCharging(charging);
            vehicle.setBatteryPercent(session.getCurrentBatteryPercent());
            vehicle.setTelemetryUpdatedAt(LocalDateTime.now());
            if (!charging) {
                vehicle.setLastChargingStation(station.getName());
                vehicle.setLastChargingAddress(station.getAddress());
                vehicle.setLastChargedAt(LocalDateTime.now());
            }
            vehicleRepository.save(vehicle);
        });
    }

    private void markConnectorCharging(ChargingConnector connector, ChargingSession session) {
        connector.setStatus(ChargerStatus.CHARGING);
        connector.setAvailable(false);
        connector.setMaintenanceMode(false);
        connector.setCurrentPowerKw(session.getPowerKw());
        connector.setSessionEnergyKwh(0);
        connector.setSessionStartedAt(session.getStartedAt());
        connector.setLastHeartbeat(LocalDateTime.now());
        connectorRepository.save(connector);
    }

    private void syncConnectorTelemetry(ChargingSession session) {
        if (session.getConnectorId() == null || session.getStatus() != ChargingSessionStatus.ACTIVE) return;
        connectorRepository.findById(session.getConnectorId()).ifPresent(connector -> {
            connector.setStatus(ChargerStatus.CHARGING);
            connector.setAvailable(false);
            connector.setCurrentPowerKw(session.getPowerKw());
            connector.setSessionEnergyKwh(session.getEnergyKwh());
            connector.setSessionStartedAt(session.getStartedAt());
            connector.setLastHeartbeat(LocalDateTime.now());
            connectorRepository.save(connector);
        });
    }

    private void releaseConnector(ChargingSession session) {
        if (session.getConnectorId() == null) return;
        connectorRepository.findById(session.getConnectorId()).ifPresent(connector -> {
            connector.setStatus(ChargerStatus.ONLINE);
            connector.setAvailable(true);
            connector.setCurrentPowerKw(0);
            connector.setSessionStartedAt(null);
            connector.setLastHeartbeat(LocalDateTime.now());
            connectorRepository.save(connector);
        });
    }

    private Booking ownedBooking(Long userId, Long bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));
    }

    private ChargingSession ownedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging session not found"));
    }

    private ChargingSessionResponse map(ChargingSession session) {
        Booking booking = bookingRepository.findById(session.getBookingId()).orElseThrow();
        String vehicleName = session.getVehicleId() == null ? null : vehicleRepository.findById(session.getVehicleId())
                .map(Vehicle::getMakeAndModel).orElse(null);
        return ChargingSessionResponse.builder()
                .id(session.getId()).bookingId(session.getBookingId()).stationId(session.getStationId())
                .connectorId(session.getConnectorId())
                .stationName(booking.getStationName()).vehicleId(session.getVehicleId()).vehicleName(vehicleName)
                .status(session.getStatus()).paymentStatus(session.getPaymentStatus()).powerKw(session.getPowerKw())
                .energyKwh(session.getEnergyKwh()).cost(session.getCost())
                .co2SavedKg(round(session.getEnergyKwh() * 0.82))
                .startBatteryPercent(session.getStartBatteryPercent())
                .currentBatteryPercent(session.getCurrentBatteryPercent())
                .targetBatteryPercent(session.getTargetBatteryPercent()).startedAt(session.getStartedAt())
                .telemetrySource(session.getVehicleId() == null ? VehicleTelemetrySource.NOT_AVAILABLE.name()
                        : vehicleRepository.findById(session.getVehicleId())
                        .map(Vehicle::getTelemetrySource).orElse(VehicleTelemetrySource.NOT_AVAILABLE).name())
                .estimatedCompletionAt(session.getEstimatedCompletionAt()).completedAt(session.getCompletedAt())
                .updatedAt(session.getUpdatedAt()).build();
    }

    private double parseCapacity(String value) {
        if (value == null) return 0;
        try { return Double.parseDouble(value.replaceAll("[^0-9.]", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private double effectiveBatteryPower(ChargingConnector connector, Vehicle vehicle, int batteryPercent) {
        double vehicleLimit = 50.0;
        double efficiency = 0.90;
        if (vehicle != null) {
            boolean acConnector = connector.getType() == ConnectorType.TYPE1 || connector.getType() == ConnectorType.TYPE2;
            Double configuredLimit = acConnector ? vehicle.getMaxAcChargePowerKw() : vehicle.getMaxDcChargePowerKw();
            if (configuredLimit != null && configuredLimit > 0) vehicleLimit = configuredLimit;
            if (vehicle.getChargingEfficiency() != null && vehicle.getChargingEfficiency() > 0
                    && vehicle.getChargingEfficiency() <= 1) efficiency = vehicle.getChargingEfficiency();
        }
        double curveLimit = batteryPercent < 60 ? vehicleLimit
                : batteryPercent < 80 ? vehicleLimit * 0.80 : vehicleLimit * 0.40;
        return round(Math.min(connector.getPowerKw(), Math.min(vehicleLimit, curveLimit)) * efficiency);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
