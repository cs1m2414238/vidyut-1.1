package com.vidyut.outlet.service;

import com.vidyut.account.entity.Account;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.entity.BookingStatus;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.outlet.dto.*;
import com.vidyut.outlet.entity.*;
import com.vidyut.outlet.repository.OutletPricingTierRepository;
import com.vidyut.outlet.repository.OutletVerificationRepository;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OutletAccessService {
    private final ChargingStationRepository stationRepository;
    private final OutletPricingTierRepository tierRepository;
    private final OutletVerificationRepository verificationRepository;
    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public OutletRateDecision resolveRate(Long userId, Long stationId, double defaultRate) {
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging station not found"));
        if (!station.isOutletPartner()) {
            return new OutletRateDecision(false, null, null, defaultRate, defaultRate);
        }
        List<OutletPricingTier> tiers = tierRepository.findByStationIdOrderByPriorityAsc(stationId);
        OutletPricingTier visitor = tiers.stream()
                .filter(tier -> tier.getEligibility() == OutletTierEligibility.VISITOR)
                .findFirst().orElse(null);
        double visitorRate = visitor == null ? defaultRate : visitor.getRatePerKwh();
        OutletPricingTier eligible = eligibleTier(userId, station, tiers);
        if (eligible == null) {
            return new OutletRateDecision(true, stationId,
                    visitor == null ? "Visitor" : visitor.getName(), visitorRate, visitorRate);
        }
        return new OutletRateDecision(true, stationId, eligible.getName(), eligible.getRatePerKwh(), visitorRate);
    }

    @Transactional(readOnly = true)
    public OutletTierResponse myTier(Long userId, Long stationId) {
        ChargingStation station = outletStation(stationId);
        List<OutletPricingTier> tiers = tierRepository.findByStationIdOrderByPriorityAsc(stationId);
        OutletRateDecision rate = resolveRate(userId, stationId, station.getPricePerKwh());
        OutletVerification verification = verificationRepository.findByUserIdAndStationId(userId, stationId).orElse(null);
        OutletVerificationStatus status = verification == null
                ? (station.isOutletIdVerificationRequired() ? OutletVerificationStatus.NOT_SUBMITTED
                : OutletVerificationStatus.NOT_REQUIRED)
                : verification.getStatus();
        String reason = rate.tierName().equalsIgnoreCase("Visitor")
                ? (station.isOutletIdVerificationRequired()
                ? "Upload your institution ID to unlock an eligible member rate."
                : "Your email domain does not match this outlet, so the visitor rate applies.")
                : "Your verified institution access qualifies for the " + rate.tierName() + " rate.";
        return new OutletTierResponse(stationId, station.getOutletInstitutionName(), rate.tierName(),
                rate.ratePerKwh(), reason, status,
                station.isOutletIdVerificationRequired() && status != OutletVerificationStatus.APPROVED,
                tiers.stream().map(this::mapTier).toList());
    }

    @Transactional(readOnly = true)
    public List<OutletPricingTierResponse> pricing(Long stationId) {
        outletStation(stationId);
        return tierRepository.findByStationIdOrderByPriorityAsc(stationId).stream().map(this::mapTier).toList();
    }

    @Transactional
    public OutletVerificationResponse submitVerification(Long userId, Long stationId, String documentUri) {
        ChargingStation station = outletStation(stationId);
        OutletVerification verification = verificationRepository.findByUserIdAndStationId(userId, stationId)
                .orElseGet(() -> OutletVerification.builder().userId(userId).stationId(stationId).build());
        if (verification.getStatus() == OutletVerificationStatus.APPROVED) return mapVerification(verification);
        verification.setDocumentUri(documentUri.trim());
        verification.setStatus(OutletVerificationStatus.PENDING);
        verification.setApprovedTierId(null);
        verification.setReviewNote("Submitted for " + station.getOutletInstitutionName());
        verification.setUpdatedAt(LocalDateTime.now());
        return mapVerification(verificationRepository.save(verification));
    }

    @Transactional(readOnly = true)
    public OutletStatsResponse stats(Long userId, Long stationId) {
        ChargingStation station = outletStation(stationId);
        OutletRateDecision decision = resolveRate(userId, stationId, station.getPricePerKwh());
        List<Booking> sessions = bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(booking -> booking.getStationId().equals(stationId))
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .toList();
        double spend = round(sessions.stream().mapToDouble(Booking::getTotalAmount).sum());
        double saved = round(sessions.stream().mapToDouble(booking ->
                Math.max(0, booking.getKwhDelivered() * decision.visitorRatePerKwh() - booking.getTotalAmount())).sum());
        return new OutletStatsResponse(stationId, station.getOutletInstitutionName(), sessions.size(), spend, saved);
    }

    @Transactional(readOnly = true)
    public List<OutletVerificationResponse> pendingVerifications() {
        return verificationRepository.findByStatusOrderByUpdatedAtAsc(OutletVerificationStatus.PENDING)
                .stream().map(this::mapVerification).toList();
    }

    @Transactional
    public OutletVerificationResponse review(Long verificationId, boolean approved, Long tierId, String note) {
        OutletVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet verification not found"));
        if (approved) {
            OutletPricingTier tier = tierRepository.findById(tierId == null ? -1 : tierId)
                    .orElseThrow(() -> new BadRequestException("Choose the outlet tier to approve"));
            if (!tier.getStationId().equals(verification.getStationId())) {
                throw new BadRequestException("The selected tier belongs to another outlet");
            }
            verification.setStatus(OutletVerificationStatus.APPROVED);
            verification.setApprovedTierId(tier.getId());
        } else {
            verification.setStatus(OutletVerificationStatus.REJECTED);
            verification.setApprovedTierId(null);
        }
        verification.setReviewNote(note == null ? null : note.trim());
        verification.setUpdatedAt(LocalDateTime.now());
        OutletVerification saved = verificationRepository.save(verification);
        notificationService.sendNotification(saved.getUserId(), "Institution verification updated",
                approved ? "Your outlet member rate is now active." : "Your institution ID needs attention.",
                NotificationType.SYSTEM_ALERT, "vidyut://outlet/" + saved.getStationId());
        return mapVerification(saved);
    }

    private OutletPricingTier eligibleTier(Long userId, ChargingStation station, List<OutletPricingTier> tiers) {
        OutletVerification verification = verificationRepository.findByUserIdAndStationId(userId, station.getId())
                .orElse(null);
        if (verification != null && verification.getStatus() == OutletVerificationStatus.APPROVED
                && verification.getApprovedTierId() != null) {
            return tiers.stream().filter(tier -> tier.getId().equals(verification.getApprovedTierId()))
                    .findFirst().orElse(null);
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        String domain = emailDomain(account.getEmail());
        OutletPricingTier emailTier = tiers.stream()
                .filter(tier -> tier.getEligibility() == OutletTierEligibility.EMAIL_DOMAIN)
                .filter(tier -> domain.equalsIgnoreCase(tier.getEmailDomain())
                        || containsDomain(station.getOutletEmailDomains(), domain))
                .findFirst().orElse(null);
        if (emailTier != null) return emailTier;
        return null;
    }

    private ChargingStation outletStation(Long stationId) {
        ChargingStation station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet station not found"));
        if (!station.isOutletPartner()) throw new BadRequestException("This station is not an outlet partner");
        return station;
    }

    private OutletPricingTierResponse mapTier(OutletPricingTier tier) {
        String note = switch (tier.getEligibility()) {
            case EMAIL_DOMAIN -> "Institution email: " + tier.getEmailDomain();
            case VERIFIED_ID -> "Verified institution ID required";
            case VISITOR -> "Available to every visitor";
        };
        return new OutletPricingTierResponse(tier.getId(), tier.getName(), tier.getRatePerKwh(),
                tier.getEligibility(), note);
    }

    private OutletVerificationResponse mapVerification(OutletVerification value) {
        return new OutletVerificationResponse(value.getId(), value.getStationId(), value.getStatus(),
                value.getApprovedTierId(), value.getReviewNote());
    }

    private String emailDomain(String email) {
        int separator = email == null ? -1 : email.lastIndexOf('@');
        return separator < 0 ? "" : email.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private boolean containsDomain(String domains, String target) {
        if (domains == null) return false;
        return List.of(domains.toLowerCase(Locale.ROOT).split(",")).stream()
                .map(String::trim).anyMatch(target::equals);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
