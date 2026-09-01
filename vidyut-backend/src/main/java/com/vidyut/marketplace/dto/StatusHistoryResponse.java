package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.InstallationStatus;

import java.time.LocalDateTime;

public record StatusHistoryResponse(
        Long id, InstallationStatus status, Long actorAccountId,
        String note, LocalDateTime createdAt
) {}
