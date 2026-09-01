package com.vidyut.land.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.vidyut.land.entity.OwnershipType;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandListingCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 180)
    private String title;

    @NotBlank(message = "Address is required")
    @Size(max = 500)
    private String address;

    @DecimalMin("-90.0") @DecimalMax("90.0") private double latitude;
    @DecimalMin("-180.0") @DecimalMax("180.0") private double longitude;

    private String city;
    private String state;
    @Pattern(regexp = "^$|^[0-9]{6}$", message = "PIN code must contain 6 digits") private String pincode;

    private String connectorType;
    private double powerKw;

    @DecimalMin("0.0") private Double pricePerKwh;

    @Builder.Default
    private PropertyType propertyType = PropertyType.OTHER;
    @Builder.Default
    @Min(1) @Max(1000) private Integer availableParkingBays = 1;
    @Builder.Default
    private PowerPhase powerPhase = PowerPhase.NOT_SURE;
    @Builder.Default
    @DecimalMin("0.0") private Double availableLoadKw = 0.0;
    private String operatingHours;
    @Builder.Default
    private OwnershipType ownershipType = OwnershipType.OWNED;
    private String preferredConnectorType;
    @Builder.Default
    @DecimalMin("0.0") private Double preferredPowerKw = 0.0;
    private String photoUrls;
    @Size(max = 1000)
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Ownership document URL must use http or https")
    private String ownershipDocumentUrl;
    @Size(max = 1000)
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Electricity document URL must use http or https")
    private String electricityDocumentUrl;
    @Size(max = 1000)
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Video verification URL must use http or https")
    private String videoVerificationUrl;
    @Builder.Default
    private Boolean discoverable = true;
}
