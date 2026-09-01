package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.BusinessModel;
import com.vidyut.marketplace.entity.ChargerCurrentType;
import com.vidyut.station.entity.ConnectorType;
import jakarta.validation.constraints.*;

import java.util.Set;

public record ChargerProductRequest(
        @NotBlank String modelName,
        @NotBlank String manufacturer,
        @NotNull ChargerCurrentType currentType,
        @NotNull ConnectorType connectorType,
        @DecimalMin("1") @DecimalMax("500") double powerKw,
        @DecimalMin("0") double equipmentPrice,
        @DecimalMin("0") double installationPrice,
        @Min(0) @Max(120) int warrantyMonths,
        boolean amcAvailable,
        String certifications,
        String description,
        String imageUrl,
        @NotEmpty Set<BusinessModel> businessModels,
        Boolean active,
        @Size(max = 1000) String complianceDocumentUrl
) {
    public ChargerProductRequest(String modelName, String manufacturer, ChargerCurrentType currentType,
                                 ConnectorType connectorType, double powerKw, double equipmentPrice,
                                 double installationPrice, int warrantyMonths, boolean amcAvailable,
                                 String certifications, String description, String imageUrl,
                                 Set<BusinessModel> businessModels, Boolean active) {
        this(modelName, manufacturer, currentType, connectorType, powerKw, equipmentPrice,
                installationPrice, warrantyMonths, amcAvailable, certifications, description,
                imageUrl, businessModels, active, null);
    }
}
