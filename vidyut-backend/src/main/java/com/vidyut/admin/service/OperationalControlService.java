package com.vidyut.admin.service;

import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.admin.dto.AccountOperationalControlRequest;
import com.vidyut.admin.dto.AccountWarningRequest;
import com.vidyut.admin.dto.EmergencyAccountAccessRequest;
import com.vidyut.admin.entity.AccountOperationalControl;
import com.vidyut.admin.entity.AdminAccount;
import com.vidyut.admin.entity.AdminAuditLog;
import com.vidyut.admin.entity.AdminCapability;
import com.vidyut.admin.entity.AdminRole;
import com.vidyut.admin.entity.OperationalControlType;
import com.vidyut.admin.repository.AccountOperationalControlRepository;
import com.vidyut.admin.repository.AdminAuditLogRepository;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationalControlService {
    private static final Set<OperationalControlType> USER_CONTROLS = Set.of(
            OperationalControlType.RESTRICT_NEW_BOOKINGS,
            OperationalControlType.FREEZE_PAYMENTS,
            OperationalControlType.REQUIRE_USER_VERIFICATION,
            OperationalControlType.TEMPORARILY_RESTRICT_ACCESS);
    private static final Set<OperationalControlType> HOST_CONTROLS = Set.of(
            OperationalControlType.PAUSE_NEW_LISTINGS,
            OperationalControlType.FREEZE_PAYOUTS,
            OperationalControlType.SUSPEND_NEW_PARTNERSHIPS,
            OperationalControlType.REQUIRE_SITE_REVERIFICATION);
    private static final Set<OperationalControlType> COMPANY_CONTROLS = Set.of(
            OperationalControlType.PAUSE_COMPANY_BOOKINGS,
            OperationalControlType.DISABLE_STATION_PUBLISHING,
            OperationalControlType.FREEZE_SETTLEMENTS,
            OperationalControlType.SUSPEND_MARKETPLACE_ACCESS,
            OperationalControlType.REQUIRE_COMPLIANCE_REVIEW);

    private final AccountOperationalControlRepository controlRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final ChargingStationRepository stationRepository;
    private final AdminAccessService access;
    private final AdminAuditLogRepository auditRepository;
    private final NotificationService notificationService;

    @Transactional
    public Map<String, Object> update(Long accountId, AccountOperationalControlRequest request) {
        AdminAccount admin = access.require(AdminCapability.ACCOUNTS);
        Account account = account(accountId);
        validateScope(account, request.control());
        if (request.control() == OperationalControlType.TEMPORARILY_RESTRICT_ACCESS
                && admin.getAdminRole() != AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException("Only a Super Admin can temporarily restrict all new account actions");
        }

        AccountOperationalControl control = current(accountId);
        String before = String.valueOf(value(control, request.control()));
        apply(control, request);
        control.setReason(request.reason().trim());
        control.setUpdatedByAdminId(admin.getAccountId());
        control.setUpdatedAt(LocalDateTime.now());
        AccountOperationalControl saved = controlRepository.save(control);

        String label = request.control().name().replace('_', ' ').toLowerCase();
        notificationService.sendNotification(accountId,
                request.enabled() ? "Vidyut operational control applied" : "Vidyut operational control removed",
                (request.enabled() ? "Admin applied " : "Admin removed ") + label + ". " + request.reason().trim(),
                NotificationType.SYSTEM_ALERT);
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId())
                .action(request.enabled() ? "APPLY_SCOPED_CONTROL" : "REMOVE_SCOPED_CONTROL")
                .resourceType("ACCOUNT_CAPABILITY").resourceId(accountId + ":" + request.control())
                .summary(account.getEmail() + " · " + label).previousValue(before)
                .newValue(String.valueOf(value(saved, request.control()))).reason(request.reason().trim()).build());
        return view(saved);
    }

    @Transactional
    public Map<String, Object> sendWarning(Long accountId, AccountWarningRequest request) {
        AdminAccount admin = access.require(AdminCapability.ACCOUNTS);
        Account account = account(accountId);
        AccountOperationalControl control = current(accountId);
        control.setWarningMessage(request.message().trim());
        control.setUpdatedByAdminId(admin.getAccountId());
        control.setUpdatedAt(LocalDateTime.now());
        controlRepository.save(control);
        notificationService.sendNotification(accountId, "Important notice from Vidyut", request.message().trim(),
                NotificationType.SYSTEM_ALERT);
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId())
                .action("SEND_ACCOUNT_WARNING").resourceType("ACCOUNT").resourceId(String.valueOf(accountId))
                .summary(account.getEmail()).newValue("Warning delivered").reason(request.message().trim()).build());
        return view(control);
    }

    @Transactional
    public void emergencyIdentityAccess(Long accountId, EmergencyAccountAccessRequest request) {
        AdminAccount admin = access.require(AdminCapability.ACCOUNTS);
        if (admin.getAdminRole() != AdminRole.SUPER_ADMIN) {
            throw new ForbiddenException("Only a Super Admin can change identity access");
        }
        Account account = account(accountId);
        if (account.getAccountType() == AccountType.ADMIN) {
            throw new BadRequestException("Use Admin staff access controls for administrator identities");
        }
        boolean before = account.isEnabled();
        account.setEnabled(request.enabled());
        accountRepository.save(account);
        auditRepository.save(AdminAuditLog.builder().adminAccountId(admin.getAccountId())
                .action(request.enabled() ? "RESTORE_IDENTITY_ACCESS" : "EMERGENCY_DISABLE_IDENTITY")
                .resourceType("ACCOUNT_IDENTITY").resourceId(String.valueOf(accountId)).summary(account.getEmail())
                .previousValue(String.valueOf(before)).newValue(String.valueOf(request.enabled()))
                .reason(request.reason().trim()).build());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> viewFor(Long accountId) {
        return view(controlRepository.findById(accountId).orElseGet(() -> blank(accountId)));
    }

    @Transactional(readOnly = true)
    public void assertBookingAllowed(Long userId, ChargingStation station) {
        AccountOperationalControl user = controlRepository.findById(userId).orElse(null);
        if (user != null) {
            if (activeAccessRestriction(user)) throw new ForbiddenException("New actions are temporarily restricted for this account");
            if (user.isRequireUserVerification()) throw new ForbiddenException("Complete the requested verification before creating a booking");
            if (user.isRestrictNewBookings()) throw new ForbiddenException("New bookings are temporarily restricted for this account");
        }
        if (station.getOperatorCompanyId() != null) {
            companyRepository.findById(station.getOperatorCompanyId()).ifPresent(company -> {
                AccountOperationalControl operator = controlRepository.findById(company.getAccount().getId()).orElse(null);
                if (operator != null && operator.isPauseCompanyBookings()) {
                    throw new ForbiddenException("This operator is not accepting new bookings at the moment");
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public void assertPaymentAllowed(Long userId) {
        AccountOperationalControl control = controlRepository.findById(userId).orElse(null);
        if (control == null) return;
        if (activeAccessRestriction(control)) throw new ForbiddenException("New actions are temporarily restricted for this account");
        if (control.isRequireUserVerification()) throw new ForbiddenException("Complete the requested verification before making payments");
        if (control.isFreezePayments()) throw new ForbiddenException("Payments and wallet activity are temporarily frozen for this account");
    }

    @Transactional(readOnly = true)
    public void assertHostCanCreateListing(Long accountId) {
        AccountOperationalControl control = controlRepository.findById(accountId).orElse(null);
        if (control != null && (activeAccessRestriction(control) || control.isPauseNewListings()
                || control.isRequireSiteReverification())) {
            throw new ForbiddenException("New property listings are paused pending Admin review");
        }
    }

    @Transactional(readOnly = true)
    public void assertHostCanStartPartnership(Long accountId) {
        AccountOperationalControl control = controlRepository.findById(accountId).orElse(null);
        if (control != null && (activeAccessRestriction(control) || control.isSuspendNewPartnerships()
                || control.isRequireSiteReverification())) {
            throw new ForbiddenException("New Host partnerships are paused pending Admin review");
        }
    }

    @Transactional(readOnly = true)
    public void assertHostPayoutAllowed(Long accountId) {
        AccountOperationalControl control = controlRepository.findById(accountId).orElse(null);
        if (control != null && (activeAccessRestriction(control) || control.isFreezePayouts())) {
            throw new ForbiddenException("Host payouts are frozen pending Finance review");
        }
    }

    @Transactional(readOnly = true)
    public void assertCompanyMarketplaceAllowed(Long accountId) {
        AccountOperationalControl control = controlRepository.findById(accountId).orElse(null);
        if (control != null && (activeAccessRestriction(control) || control.isSuspendMarketplaceAccess()
                || control.isRequireComplianceReview())) {
            throw new ForbiddenException("Company marketplace actions are paused pending compliance review");
        }
    }

    @Transactional(readOnly = true)
    public void assertCompanyPublishingAllowed(Long accountId) {
        AccountOperationalControl control = controlRepository.findById(accountId).orElse(null);
        if (control != null && (activeAccessRestriction(control) || control.isDisableStationPublishing()
                || control.isRequireComplianceReview())) {
            throw new ForbiddenException("Station and charger publishing is paused pending compliance review");
        }
    }

    @Transactional(readOnly = true)
    public void assertSettlementCanBePaid(Long stationId) {
        if (stationId == null) return;
        ChargingStation station = stationRepository.findById(stationId).orElse(null);
        if (station == null || station.getOperatorCompanyId() == null) return;
        companyRepository.findById(station.getOperatorCompanyId()).ifPresent(company -> {
            AccountOperationalControl control = controlRepository.findById(company.getAccount().getId()).orElse(null);
            if (control != null && control.isFreezeSettlements()) {
                throw new ForbiddenException("Company settlements are frozen pending Finance review");
            }
        });
    }

    private Account account(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private AccountOperationalControl current(Long accountId) {
        return controlRepository.findById(accountId).orElseGet(() -> blank(accountId));
    }

    private AccountOperationalControl blank(Long accountId) {
        return AccountOperationalControl.builder().accountId(accountId).build();
    }

    private boolean activeAccessRestriction(AccountOperationalControl control) {
        return control.getAccessRestrictedUntil() != null
                && control.getAccessRestrictedUntil().isAfter(LocalDateTime.now());
    }

    private void validateScope(Account account, OperationalControlType type) {
        boolean allowed = (USER_CONTROLS.contains(type) && account.getRoles().contains(AccountRole.ROLE_EV_USER))
                || (HOST_CONTROLS.contains(type) && account.getRoles().contains(AccountRole.ROLE_HOST))
                || (COMPANY_CONTROLS.contains(type) && account.getAccountType() == AccountType.COMPANY);
        if (!allowed) throw new BadRequestException(type + " does not apply to this account partition");
    }

    private void apply(AccountOperationalControl control, AccountOperationalControlRequest request) {
        boolean enabled = request.enabled();
        switch (request.control()) {
            case RESTRICT_NEW_BOOKINGS -> control.setRestrictNewBookings(enabled);
            case FREEZE_PAYMENTS -> control.setFreezePayments(enabled);
            case REQUIRE_USER_VERIFICATION -> control.setRequireUserVerification(enabled);
            case TEMPORARILY_RESTRICT_ACCESS -> control.setAccessRestrictedUntil(enabled
                    ? LocalDateTime.now().plusHours(request.durationHours() == null ? 24 : request.durationHours()) : null);
            case PAUSE_NEW_LISTINGS -> control.setPauseNewListings(enabled);
            case FREEZE_PAYOUTS -> control.setFreezePayouts(enabled);
            case SUSPEND_NEW_PARTNERSHIPS -> control.setSuspendNewPartnerships(enabled);
            case REQUIRE_SITE_REVERIFICATION -> control.setRequireSiteReverification(enabled);
            case PAUSE_COMPANY_BOOKINGS -> control.setPauseCompanyBookings(enabled);
            case DISABLE_STATION_PUBLISHING -> control.setDisableStationPublishing(enabled);
            case FREEZE_SETTLEMENTS -> control.setFreezeSettlements(enabled);
            case SUSPEND_MARKETPLACE_ACCESS -> control.setSuspendMarketplaceAccess(enabled);
            case REQUIRE_COMPLIANCE_REVIEW -> control.setRequireComplianceReview(enabled);
        }
    }

    private Object value(AccountOperationalControl control, OperationalControlType type) {
        return switch (type) {
            case RESTRICT_NEW_BOOKINGS -> control.isRestrictNewBookings();
            case FREEZE_PAYMENTS -> control.isFreezePayments();
            case REQUIRE_USER_VERIFICATION -> control.isRequireUserVerification();
            case TEMPORARILY_RESTRICT_ACCESS -> control.getAccessRestrictedUntil();
            case PAUSE_NEW_LISTINGS -> control.isPauseNewListings();
            case FREEZE_PAYOUTS -> control.isFreezePayouts();
            case SUSPEND_NEW_PARTNERSHIPS -> control.isSuspendNewPartnerships();
            case REQUIRE_SITE_REVERIFICATION -> control.isRequireSiteReverification();
            case PAUSE_COMPANY_BOOKINGS -> control.isPauseCompanyBookings();
            case DISABLE_STATION_PUBLISHING -> control.isDisableStationPublishing();
            case FREEZE_SETTLEMENTS -> control.isFreezeSettlements();
            case SUSPEND_MARKETPLACE_ACCESS -> control.isSuspendMarketplaceAccess();
            case REQUIRE_COMPLIANCE_REVIEW -> control.isRequireComplianceReview();
        };
    }

    private Map<String, Object> view(AccountOperationalControl control) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("restrictNewBookings", control.isRestrictNewBookings());
        view.put("freezePayments", control.isFreezePayments());
        view.put("requireUserVerification", control.isRequireUserVerification());
        view.put("accessRestrictedUntil", control.getAccessRestrictedUntil());
        view.put("pauseNewListings", control.isPauseNewListings());
        view.put("freezePayouts", control.isFreezePayouts());
        view.put("suspendNewPartnerships", control.isSuspendNewPartnerships());
        view.put("requireSiteReverification", control.isRequireSiteReverification());
        view.put("pauseCompanyBookings", control.isPauseCompanyBookings());
        view.put("disableStationPublishing", control.isDisableStationPublishing());
        view.put("freezeSettlements", control.isFreezeSettlements());
        view.put("suspendMarketplaceAccess", control.isSuspendMarketplaceAccess());
        view.put("requireComplianceReview", control.isRequireComplianceReview());
        view.put("warningMessage", control.getWarningMessage());
        view.put("reason", control.getReason());
        view.put("updatedAt", control.getUpdatedAt());
        return view;
    }
}
