package com.vidyut.marketplace.dto;

import java.time.LocalDateTime;

/** Public marketplace projection; customer contact and report details are never exposed. */
public record HostReviewSummaryResponse(
        int rating,
        Long stationId,
        String stationName,
        String stationCity,
        String reviewerName,
        String comment,
        String hostReply,
        LocalDateTime createdAt
) {}
