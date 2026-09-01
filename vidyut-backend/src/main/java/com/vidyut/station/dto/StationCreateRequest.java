package com.vidyut.station.dto;

import com.vidyut.station.entity.ConnectorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class StationCreateRequest {

    @NotBlank(message = "Station name is required")
    @Size(max = 180)
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 500)
    private String address;

    private String city;
    @DecimalMin("-90.0") @DecimalMax("90.0") private double latitude;
    @DecimalMin("-180.0") @DecimalMax("180.0") private double longitude;

    @NotNull(message = "Price per kWh is required")
    @DecimalMin(value = "0.01", message = "Price per kWh must be greater than zero")
    private Double pricePerKwh;

    @NotNull(message = "Connector type is required")
    private ConnectorType connectorType;
    @DecimalMin(value = "0.1", message = "Charging power must be greater than zero")
    private double powerKw;
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
    private boolean autoAvailability;
    @Min(0) @Max(480) private int bookingSlotMinutes;
}
