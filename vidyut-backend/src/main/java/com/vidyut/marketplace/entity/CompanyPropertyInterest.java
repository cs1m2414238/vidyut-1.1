package com.vidyut.marketplace.entity;

import com.vidyut.company.entity.Company;
import com.vidyut.land.entity.LandListing;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_property_interests", uniqueConstraints =
        @UniqueConstraint(columnNames = {"company_id", "land_listing_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPropertyInterest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "land_listing_id", nullable = false)
    private LandListing property;

    @Column(length = 1200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InterestStatus status = InterestStatus.PENDING;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
