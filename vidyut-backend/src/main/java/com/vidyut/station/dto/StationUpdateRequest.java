package com.vidyut.station.dto;

import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationUpdateRequest {
    @Size(min = 1, max = 180) private String name;
    @Size(min = 1, max = 500) private String address;
    @DecimalMin(value = "0.01", message = "Price per kWh must be greater than zero") private Double pricePerKwh;
    private StationStatus status;
    private StationAvailability availability;
    private String city;
    @DecimalMin("-90.0") @DecimalMax("90.0") private Double latitude;
    @DecimalMin("-180.0") @DecimalMax("180.0") private Double longitude;
    private String imageUrl;
    private String photoUrls;
    private String amenities;
    private String workingHours;
    private String weeklySchedule;
    private String holidaySchedule;
    private String chargingInstructions;
    @Size(max = 120) private String propertyOwnerName;
    @Size(max = 160) private String operatorCompanyName;
    @Size(max = 160) private String equipmentOwnerName;
    @Size(max = 80) private String operatingModel;
    @Size(max = 160) private String solarProviderName;
    @Size(max = 1000) private String siteOwnershipDocumentUrl;
    @Size(max = 1000) private String electricityConnectionDocumentUrl;
    private Boolean autoAvailability;
    private Boolean emergencyDisabled;
    @Min(15) @Max(480) private Integer bookingSlotMinutes;
    @Min(0) private Integer queueCount;
    @DecimalMin("0.0") @DecimalMax("100.0") private Double occupancyPercent;
}
