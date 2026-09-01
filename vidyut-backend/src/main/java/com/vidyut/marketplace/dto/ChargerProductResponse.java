package com.vidyut.marketplace.dto;

import com.vidyut.marketplace.entity.BusinessModel;
import com.vidyut.marketplace.entity.ChargerCurrentType;
import com.vidyut.station.entity.ConnectorType;

import java.util.Set;

public record ChargerProductResponse(
        Long id, Long companyId, String companyName, String modelName, String manufacturer,
        ChargerCurrentType currentType, ConnectorType connectorType, double powerKw,
        double equipmentPrice, double installationPrice, int warrantyMonths,
        boolean amcAvailable, String certifications, String description, String imageUrl,
        Set<BusinessModel> businessModels, boolean active, String complianceDocumentUrl,
        com.vidyut.marketplace.entity.ProductApprovalStatus approvalStatus, String adminReviewNote
) {}
