package com.vidyut.marketplace.entity;

import com.vidyut.company.entity.Company;
import com.vidyut.station.entity.ConnectorType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "charger_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargerProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 140)
    private String modelName;

    @Column(nullable = false, length = 140)
    private String manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChargerCurrentType currentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectorType connectorType;

    private double powerKw;
    private double equipmentPrice;
    private double installationPrice;
    private int warrantyMonths;
    private boolean amcAvailable;

    @Column(length = 300)
    private String certifications;

    @Column(length = 1500)
    private String description;

    @Column(length = 600)
    private String imageUrl;

    @Column(length = 1000)
    private String complianceDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ProductApprovalStatus approvalStatus = ProductApprovalStatus.PENDING_REVIEW;

    @Column(length = 800)
    private String adminReviewNote;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "charger_product_business_models", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "business_model", nullable = false, length = 30)
    @Builder.Default
    private Set<BusinessModel> businessModels = new HashSet<>();

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
