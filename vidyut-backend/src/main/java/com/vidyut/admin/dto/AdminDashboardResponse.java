package com.vidyut.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalStations;
    private long totalBookings;
    private long totalCompanies;
    private double totalNetworkKwhDelivered;
    private double totalRevenueGenerated;
}
