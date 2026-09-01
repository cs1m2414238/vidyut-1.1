package com.vidyut.company.dto;

import com.vidyut.company.entity.CompanyAgentMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record CompanyAgentResponse(
        String intent,
        CompanyAgentMode mode,
        String answer,
        NetworkSummary network,
        FaultImpact fault,
        RevenueSummary revenue,
        PricingRecommendation pricing,
        List<SiteRecommendation> siteRecommendations,
        List<RecommendedAction> actions,
        Map<String, Object> offerDraft,
        Map<String, Object> operations,
        String assistantModel,
        String assistantProvider,
        boolean assistantFallback,
        LocalDateTime generatedAt
) {
    public record NetworkSummary(
            int stations,
            int chargers,
            int occupied,
            int available,
            int reserved,
            int offline,
            int faults,
            int activeSessions,
            String highestLoadStation,
            double highestLoadPercent,
            String longestSessionCharger,
            long longestSessionMinutes,
            String nextAvailableCharger,
            long nextAvailableMinutes
    ) {}

    public record FaultImpact(
            Long chargerId,
            String chargerCode,
            String stationName,
            String issue,
            int affectedBookings,
            Integer estimatedDowntimeMinutes,
            double estimatedRevenueAtRisk,
            List<String> compatibleBackups
    ) {}

    public record RevenueSummary(
            long sessions,
            double energySoldKwh,
            double chargingRevenue,
            Double estimatedHostPayouts,
            Double estimatedVidyutFees,
            double refunds,
            Double estimatedCompanyRevenue,
            String bestPerformingStation,
            String lowestPerformingStation
    ) {}

    public record PricingRecommendation(
            Long stationId,
            String stationName,
            double currentPricePerKwh,
            double nearbyAveragePricePerKwh,
            double recommendedPricePerKwh,
            double currentUtilizationPercent,
            Double expectedUtilizationPercent,
            String timeWindow
    ) {}

    public record SiteRecommendation(
            Long propertyId,
            String title,
            String location,
            int parkingBays,
            double availableLoadKw,
            Double nearestActiveStationKm,
            double expansionScore,
            int recommendedChargerCount,
            double recommendedPowerKw,
            String recommendedConnector,
            String reason
    ) {}

    public record RecommendedAction(
            CompanyAgentActionType action,
            String label,
            String risk,
            boolean requiresApproval,
            Long chargerId,
            Long stationId,
            Double proposedPricePerKwh,
            String reason,
            com.vidyut.station.entity.ChargerStatus expectedStatus
    ) {
        public RecommendedAction(CompanyAgentActionType action, String label, String risk, boolean requiresApproval,
                Long chargerId, Long stationId, Double proposedPricePerKwh, String reason) {
            this(action, label, risk, requiresApproval, chargerId, stationId, proposedPricePerKwh, reason, null);
        }
    }
}
