package com.vidyut.autopilot.dto;

public record AutopilotTripSummaryResponse(Long tripId, String origin, String destination,
                                           double totalKm, int totalMinutes, int chargingMinutes,
                                           int stopsTaken, double totalCost, double co2SavedKg,
                                           String shareText) {}
