package com.vidyut.admin.dto;

import com.vidyut.company.dto.CompanyVerificationResponse;

import java.util.List;
import java.util.Map;

public record AdminPortalSnapshot(
        Map<String, Long> metrics,
        List<Map<String, Object>> accounts,
        List<CompanyVerificationResponse> companyVerifications,
        List<CompanyVerificationResponse> companyVerificationHistory,
        List<Map<String, Object>> hostVerifications,
        List<Map<String, Object>> properties,
        List<Map<String, Object>> products,
        List<Map<String, Object>> stations,
        List<Map<String, Object>> connectors,
        List<Map<String, Object>> activeSessions,
        List<Map<String, Object>> incidents,
        List<Map<String, Object>> maintenanceTickets,
        List<Map<String, Object>> installations,
        List<Map<String, Object>> settlements,
        List<Map<String, Object>> supportCases,
        List<Map<String, Object>> greenSchemes,
        List<Map<String, Object>> bookings,
        List<Map<String, Object>> payments,
        List<Map<String, Object>> autopilotTrips,
        List<Map<String, Object>> networkSuggestions,
        List<Map<String, Object>> announcements
) {}
