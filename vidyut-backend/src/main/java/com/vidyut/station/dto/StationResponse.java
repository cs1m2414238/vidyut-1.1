package com.vidyut.station.dto;

import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.entity.StationOwnershipType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationResponse {
    private Long id;
    private String name;
    private String address;
    private String city;
    private double latitude;
    private double longitude;
    private double pricePerKwh;
    private double rating;
    private int reviewCount;
    private String imageUrl;
    private String photoUrls;
    private String amenities;
    private String workingHours;
    private String weeklySchedule;
    private String holidaySchedule;
    private String chargingInstructions;
    private boolean autoAvailability;
    private boolean emergencyDisabled;
    private boolean demoData;
    private String propertyOwnerName;
    private String operatorCompanyName;
    private String equipmentOwnerName;
    private String operatingModel;
    private String solarProviderName;
    private int bookingSlotMinutes;
    private int queueCount;
    private double occupancyPercent;
    private boolean dynamicPricingEnabled;
    private Double timeBasedPricePerHour;
    private Double peakPricePerKwh;
    private String peakHours;
    private Double studentDiscountPercent;
    private Double corporatePricePerKwh;
    private String couponCode;
    private Double couponDiscountPercent;
    private boolean outletPartner;
    private String outletInstitutionName;
    private boolean outletIdVerificationRequired;
    private StationStatus status;
    private StationAvailability availability;
    private Long hostUserId;
    private Long propertyOwnerAccountId;
    private Long operatorCompanyId;
    private Long hostPartnershipId;
    private StationOwnershipType ownershipType;
    private boolean siteEvidenceComplete;
    private List<ChargingConnector> connectors;
    private int totalSlots;
    private int availableSlots;
    private String liveStatus;
    private Double distanceKm;
}
