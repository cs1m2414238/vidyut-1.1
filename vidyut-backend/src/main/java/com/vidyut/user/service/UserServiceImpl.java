package com.vidyut.user.service;

import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountRole;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.entity.EvUserProfile;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.account.entity.HostVerificationStatus;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.EvUserProfileRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.company.entity.Company;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.user.dto.UpdateUserRequest;
import com.vidyut.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import com.vidyut.vehicle.repository.VehicleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CompanyRepository companyRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getUserById(Long id) {
        return map(accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id)));
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        return map(accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with email: " + email)));
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return accountRepository.findAll().stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        if (account.getAccountType() == AccountType.COMPANY) {
            Company company = companyRepository.findByAccount_Id(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Company profile not found"));
            if (request.getFullName() != null) company.setContactName(request.getFullName());
            if (request.getPhone() != null) company.setSupportPhone(request.getPhone());
            companyRepository.save(company);
        } else {
            evUserProfileRepository.findById(id).ifPresent(profile -> {
                if (request.getFullName() != null) profile.setFullName(request.getFullName());
                if (request.getPhone() != null) profile.setPhone(request.getPhone());
                evUserProfileRepository.save(profile);
            });
            hostProfileRepository.findById(id).ifPresent(profile -> {
                if (request.getFullName() != null) profile.setDisplayName(request.getFullName());
                hostProfileRepository.save(profile);
            });
        }
        return map(account);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        if (account.getEmail() != null && account.getEmail().toLowerCase().startsWith("demo.") && account.getEmail().toLowerCase().endsWith("@vidyut.com")) {
            throw new com.vidyut.common.exception.BadRequestException("Public demo accounts cannot be deleted.");
        }
        account.setEnabled(false);
        account.setGoogleSubject(null);
        account.setEmailVerified(false);
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setEmail("deleted+" + id + "+" + System.currentTimeMillis() + "@deleted.vidyut.local");

        evUserProfileRepository.findById(id).ifPresent(profile -> {
            profile.setFullName("Deleted account");
            profile.setPhone(null);
            evUserProfileRepository.save(profile);
        });
        hostProfileRepository.findById(id).ifPresent(profile -> {
            profile.setDisplayName("Deleted account"); profile.setPhone(null); profile.setAddress(null);
            profile.setBio(null); profile.setKycDocumentUrl(null); profile.setIdentityType(null);
            profile.setIdentityLast4(null); profile.setBankAccountHolder(null); profile.setBankName(null);
            profile.setBankAccountLast4(null); profile.setIfscCode(null); profile.setPayoutUpi(null);
            hostProfileRepository.save(profile);
        });
        companyRepository.findByAccount_Id(id).ifPresent(company -> {
            company.setCompanyName(null); company.setRegistrationNumber(null); company.setSupportEmail(null);
            company.setSupportPhone(null); company.setGstNumber(null); company.setKycDocumentUrl(null);
            company.setBusinessAddress(null); company.setWebsite(null); company.setContactName("Deleted account");
            company.setActive(false); companyRepository.save(company);
        });
        vehicleRepository.findByUserId(id).forEach(vehicle -> {
            vehicle.setMakeAndModel("Deleted vehicle");
            vehicle.setRegistrationNumber("DELETED-" + vehicle.getId() + "-" + id);
            vehicle.setBluetoothDeviceName(null); vehicle.setLastChargingAddress(null);
            vehicle.setLastChargingStation(null); vehicleRepository.save(vehicle);
        });
        accountRepository.save(account);
    }

    private UserResponse map(Account account) {
        AccessMode mode = defaultMode(account);
        String name;
        String phone = null;
        String contactName = null;
        String companyName = null;
        String registrationNumber = null;
        HostVerificationStatus hostStatus = null;
        boolean profileCompleted = false;
        if (account.getAccountType() == AccountType.COMPANY) {
            Company company = companyRepository.findByAccount_Id(account.getId()).orElse(null);
            name = company == null ? account.getEmail()
                    : hasText(company.getCompanyName()) ? company.getCompanyName() : company.getContactName();
            phone = company == null ? null : company.getSupportPhone();
            contactName = company == null ? null : company.getContactName();
            companyName = company == null ? null : company.getCompanyName();
            registrationNumber = company == null ? null : company.getRegistrationNumber();
            profileCompleted = company != null && hasText(contactName) && validPhone(phone)
                    && hasText(companyName) && hasText(registrationNumber);
        } else {
            EvUserProfile ev = evUserProfileRepository.findById(account.getId()).orElse(null);
            HostProfile host = hostProfileRepository.findById(account.getId()).orElse(null);
            name = ev != null ? ev.getFullName() : host != null ? host.getDisplayName() : account.getEmail();
            phone = ev != null ? ev.getPhone() : host != null ? host.getPhone() : null;
            hostStatus = host == null ? null : host.getVerificationStatus();
            profileCompleted = mode == AccessMode.HOST
                    ? host != null && hasText(host.getDisplayName()) && validPhone(host.getPhone())
                    : ev != null && hasText(ev.getFullName()) && validPhone(ev.getPhone());
        }
        return UserResponse.builder()
                .id(account.getId()).email(account.getEmail()).fullName(name).phone(phone)
                .contactName(contactName).companyName(companyName).registrationNumber(registrationNumber)
                .profileCompleted(profileCompleted).emailVerified(account.isEmailVerified()).hostStatus(hostStatus)
                .role(mode.role()).accountType(account.getAccountType())
                .roles(new LinkedHashSet<>(account.getRoles()))
                .allowedModes(account.getRoles().stream().map(AccountRole::mode)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))
                .defaultMode(mode).enabled(account.isEnabled()).createdAt(account.getCreatedAt()).build();
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private boolean validPhone(String value) {
        if (value == null) return false;
        return value.replaceAll("\\D", "").matches("^(?:91)?[0-9]{10}$");
    }

    private AccessMode defaultMode(Account account) {
        if (account.allows(AccessMode.EV_USER)) return AccessMode.EV_USER;
        if (account.allows(AccessMode.HOST)) return AccessMode.HOST;
        if (account.allows(AccessMode.COMPANY)) return AccessMode.COMPANY;
        return AccessMode.ADMIN;
    }
}
