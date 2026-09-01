package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.InterestStatus;

import java.time.LocalDateTime;

public record PropertyInterestResponse(
        Long id, Long companyId, String companyName, Long propertyId, String propertyTitle,
        String propertyCity, String message, InterestStatus status, LocalDateTime createdAt,
        boolean contactUnlocked, String companyEmail, String companyPhone,
        String hostEmail, String hostPhone
) {}
