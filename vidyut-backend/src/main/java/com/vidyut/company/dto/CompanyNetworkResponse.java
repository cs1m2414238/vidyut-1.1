package com.vidyut.company.dto;

import java.util.List;

public record CompanyNetworkResponse(
        int totalStations,
        int totalChargers,
        int onlineChargers,
        int chargingChargers,
        int faultedChargers,
        int openMaintenanceTickets,
        List<ManagedStationResponse> stations,
        List<ManagedChargerResponse> chargers
) {}
