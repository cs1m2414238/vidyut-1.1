package com.vidyut.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_operational_controls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountOperationalControl {
    @Id
    private Long accountId;

    private boolean restrictNewBookings;
    private boolean freezePayments;
    private boolean requireUserVerification;
    private LocalDateTime accessRestrictedUntil;
    private boolean pauseNewListings;
    private boolean freezePayouts;
    private boolean suspendNewPartnerships;
    private boolean requireSiteReverification;
    private boolean pauseCompanyBookings;
    private boolean disableStationPublishing;
    private boolean freezeSettlements;
    private boolean suspendMarketplaceAccess;
    private boolean requireComplianceReview;

    @Column(length = 1200)
    private String warningMessage;

    @Column(length = 1200)
    private String reason;

    private Long updatedByAdminId;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
