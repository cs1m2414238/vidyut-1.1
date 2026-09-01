package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.BusinessModel;
import com.vidyut.marketplace.entity.InstallationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InstallationRequestResponse(
        Long id, Long hostUserId, Long propertyId, String propertyTitle, String propertyAddress,
        String propertyCity, Long companyId, String companyName, Long productId, String productName,
        String connectorType, double powerKw, int quantity, BusinessModel businessModel,
        Double budget, LocalDate targetInstallationDate, String hostMessage, String companyNote,
        LocalDate scheduledSurveyAt, LocalDate scheduledInstallationAt, Long stationId,
        InstallationStatus status, ProposalResponse proposal, List<StatusHistoryResponse> history,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {}
