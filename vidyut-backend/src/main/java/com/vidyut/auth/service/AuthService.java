package com.vidyut.auth.service;

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
import com.vidyut.auth.dto.AuthResponse;
import com.vidyut.auth.dto.CompleteProfileRequest;
import com.vidyut.auth.dto.GoogleAuthRequest;
import com.vidyut.auth.dto.LoginRequest;
import com.vidyut.auth.dto.RegisterCompanyRequest;
import com.vidyut.auth.dto.RegisterUserRequest;
import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ForbiddenException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.common.exception.UnauthorizedException;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.security.JwtService;
import com.vidyut.user.dto.UserResponse;
import com.vidyut.wallet.entity.EvWallet;
import com.vidyut.wallet.repository.EvWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final EvUserProfileRepository evUserProfileRepository;
    private final HostProfileRepository hostProfileRepository;
    private final CompanyRepository companyRepository;
    private final EvWalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RestClient googleClient = createGoogleClient();

    @Value("${vidyut.oauth.google-client-ids:${vidyut.oauth.google-client-id:}}")
    private String googleClientIds;

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        if ("tata@vidyut.demo".equalsIgnoreCase(email) && accountRepository.findByEmailIgnoreCase(email).isEmpty()) {
            email = "contactpriyanshusharma6281@gmail.com";
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Account account = findByEmail(email);
        if (account.getAccountType() == AccountType.ADMIN) {
            throw new ForbiddenException("Administrator accounts must use the separate Vidyut Admin Portal");
        }
        return buildAuthResponse(account, defaultMode(account));
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
        GoogleIdentity identity = verifyGoogleIdentity(request.getAccessToken());
        AccessMode requestedMode = request.getRequestedMode() == null ? AccessMode.EV_USER : request.getRequestedMode();
        if (requestedMode == AccessMode.ADMIN) {
            throw new ForbiddenException("Google sign-in cannot create an administrator account");
        }

        Account account = accountRepository.findByGoogleSubject(identity.subject())
                .or(() -> accountRepository.findByEmailIgnoreCase(identity.email()))
                .orElse(null);

        if (account == null) {
            account = createGoogleAccount(identity, requestedMode);
        } else {
            if (account.getGoogleSubject() != null && !account.getGoogleSubject().equals(identity.subject())) {
                throw new UnauthorizedException("This email is already linked to a different Google account");
            }
            account.setGoogleSubject(identity.subject());
            account.setEmailVerified(true);
            accountRepository.save(account);
        }

        AccessMode activeMode = account.allows(requestedMode) ? requestedMode : defaultMode(account);
        return buildAuthResponse(account, activeMode);
    }

    @Transactional
    public AuthResponse registerUser(RegisterUserRequest request) {
        Account account = createAccount(request.getEmail(), request.getPassword(),
                AccountType.INDIVIDUAL, Set.of(AccountRole.ROLE_EV_USER));
        evUserProfileRepository.save(EvUserProfile.builder()
                .account(account)
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .build());
        walletRepository.save(EvWallet.builder()
                .userId(account.getId())
                .balance(0.0)
                .build());
        return buildAuthResponse(account, AccessMode.EV_USER);
    }

    @Transactional
    public AuthResponse registerHost(RegisterUserRequest request) {
        Account account = createAccount(request.getEmail(), request.getPassword(),
                AccountType.INDIVIDUAL, Set.of(AccountRole.ROLE_HOST));
        hostProfileRepository.save(HostProfile.builder()
                .account(account)
                .displayName(request.getFullName().trim())
                .phone(request.getPhone())
                .verified(false)
                .verificationStatus(HostVerificationStatus.PENDING)
                .build());
        return buildAuthResponse(account, AccessMode.HOST);
    }

    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest request) {
        String companyName = normalizeOptional(request.getCompanyName());
        String registrationNumber = normalizeOptional(request.getRegistrationNumber());
        if (companyName != null && companyRepository.existsByCompanyName(companyName)) {
            throw new DuplicateResourceException("Company name already exists: " + companyName);
        }
        if (registrationNumber != null && companyRepository.existsByRegistrationNumber(registrationNumber)) {
            throw new DuplicateResourceException("Registration number already exists: " + registrationNumber);
        }

        Account account = createAccount(request.getAdminEmail(), request.getAdminPassword(),
                AccountType.COMPANY, Set.of(AccountRole.ROLE_COMPANY));
        companyRepository.save(Company.builder()
                .account(account)
                .companyName(companyName)
                .registrationNumber(registrationNumber)
                .supportEmail(account.getEmail())
                .supportPhone(request.getSupportPhone())
                .contactName(request.getAdminFullName().trim())
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build());
        return buildAuthResponse(account, AccessMode.COMPANY);
    }

    @Transactional
    public AuthResponse completeProfile(String email, CompleteProfileRequest request) {
        Account account = findByEmail(email);
        AccessMode mode = request.getMode();
        if (mode == AccessMode.ADMIN || !account.allows(mode)) {
            throw new ForbiddenException("This account cannot complete the requested workspace profile");
        }

        String fullName = request.getFullName().trim();
        String phone = request.getPhone().trim();

        if (mode == AccessMode.EV_USER) {
            EvUserProfile profile = evUserProfileRepository.findById(account.getId())
                    .orElseGet(() -> EvUserProfile.builder().account(account).build());
            profile.setFullName(fullName);
            profile.setPhone(phone);
            evUserProfileRepository.save(profile);
            if (walletRepository.findByUserId(account.getId()).isEmpty()) {
                walletRepository.save(EvWallet.builder().userId(account.getId()).balance(0.0).build());
            }
        } else if (mode == AccessMode.HOST) {
            HostProfile profile = hostProfileRepository.findById(account.getId())
                    .orElseGet(() -> HostProfile.builder()
                            .account(account)
                            .verified(false)
                            .verificationStatus(HostVerificationStatus.PENDING)
                            .build());
            String displayName = normalizeOptional(request.getHostDisplayName());
            profile.setDisplayName(displayName == null ? fullName : displayName);
            profile.setPhone(phone);
            hostProfileRepository.save(profile);
        } else if (mode == AccessMode.COMPANY) {
            String companyName = normalizeOptional(request.getCompanyName());
            String registrationNumber = normalizeOptional(request.getRegistrationNumber());
            if (companyName == null) throw new BadRequestException("Company name is required");
            if (registrationNumber == null) throw new BadRequestException("Registration number or CIN is required");
            registrationNumber = registrationNumber.toUpperCase();
            if (companyRepository.existsByCompanyNameAndAccount_IdNot(companyName, account.getId())) {
                throw new DuplicateResourceException("Company name already exists: " + companyName);
            }
            if (companyRepository.existsByRegistrationNumberAndAccount_IdNot(registrationNumber, account.getId())) {
                throw new DuplicateResourceException("Registration number already exists: " + registrationNumber);
            }
            Company company = companyRepository.findByAccount_Id(account.getId())
                    .orElseGet(() -> Company.builder().account(account).active(true)
                            .verificationStatus(VerificationStatus.PENDING).build());
            company.setCompanyName(companyName);
            company.setRegistrationNumber(registrationNumber);
            company.setContactName(fullName);
            company.setSupportEmail(account.getEmail());
            company.setSupportPhone(phone);
            companyRepository.save(company);
        }

        return buildAuthResponse(account, mode);
    }

    public AuthResponse switchMode(String email, AccessMode requestedMode) {
        Account account = findByEmail(email);
        if (!account.allows(requestedMode)) {
            throw new ForbiddenException("This account is not allowed to use " + requestedMode + " mode");
        }
        return buildAuthResponse(account, requestedMode);
    }

    @Transactional
    public UserResponse applyForHost(String email, String displayName) {
        Account account = findByEmail(email);
        if (account.getAccountType() != AccountType.INDIVIDUAL
                || !account.getRoles().contains(AccountRole.ROLE_EV_USER)) {
            throw new ForbiddenException("Only an individual EV account can apply for host access");
        }
        if (account.getRoles().contains(AccountRole.ROLE_HOST)) {
            return mapToUserResponse(account, AccessMode.EV_USER);
        }

        HostProfile profile = hostProfileRepository.findById(account.getId())
                .orElseGet(() -> HostProfile.builder().account(account).build());
        profile.setDisplayName(displayName == null || displayName.isBlank()
                ? displayName(account, AccessMode.EV_USER)
                : displayName.trim());
        profile.setVerified(false);
        profile.setVerificationStatus(HostVerificationStatus.PENDING);
        hostProfileRepository.save(profile);
        return mapToUserResponse(account, AccessMode.EV_USER);
    }

    @Transactional
    public UserResponse approveHost(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        if (account.getAccountType() != AccountType.INDIVIDUAL) {
            throw new BadRequestException("Company and admin accounts cannot receive HOST mode");
        }
        HostProfile profile = hostProfileRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No pending host application for account: " + accountId));
        profile.setVerified(true);
        profile.setVerificationStatus(HostVerificationStatus.VERIFIED);
        hostProfileRepository.save(profile);
        account.getRoles().add(AccountRole.ROLE_HOST);
        accountRepository.save(account);
        return mapToUserResponse(account, defaultMode(account));
    }

    private Account createAccount(String rawEmail, String password, AccountType accountType,
                                  Set<AccountRole> roles) {
        String email = normalizeEmail(rawEmail);
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Account already exists with email: " + email);
        }
        return accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .accountType(accountType)
                .roles(new LinkedHashSet<>(roles))
                .enabled(true)
                .build());
    }

    private Account createGoogleAccount(GoogleIdentity identity, AccessMode mode) {
        AccountType accountType = mode == AccessMode.COMPANY ? AccountType.COMPANY : AccountType.INDIVIDUAL;
        Account account = accountRepository.save(Account.builder()
                .email(identity.email())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .googleSubject(identity.subject())
                .accountType(accountType)
                .roles(new LinkedHashSet<>(Set.of(mode.role())))
                .emailVerified(true)
                .enabled(true)
                .build());

        if (mode == AccessMode.EV_USER) {
            evUserProfileRepository.save(EvUserProfile.builder()
                    .account(account).fullName(identity.name()).build());
            walletRepository.save(EvWallet.builder().userId(account.getId()).balance(0.0).build());
        } else if (mode == AccessMode.HOST) {
            hostProfileRepository.save(HostProfile.builder()
                    .account(account)
                    .displayName(identity.name())
                    .verified(false)
                    .verificationStatus(HostVerificationStatus.PENDING)
                    .build());
        } else {
            companyRepository.save(Company.builder()
                    .account(account)
                    .contactName(identity.name())
                    .supportEmail(identity.email())
                    .verificationStatus(VerificationStatus.PENDING)
                    .active(true)
                    .build());
        }
        return account;
    }

    private AuthResponse buildAuthResponse(Account account, AccessMode activeMode) {
        return AuthResponse.builder()
                .token(jwtService.generateModeToken(account, activeMode))
                .activeMode(activeMode)
                .user(mapToUserResponse(account, activeMode))
                .build();
    }

    public UserResponse mapToUserResponse(Account account, AccessMode activeMode) {
        Set<AccessMode> allowedModes = account.getRoles().stream()
                .map(AccountRole::mode)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Company company = account.getAccountType() == AccountType.COMPANY
                ? companyRepository.findByAccount_Id(account.getId()).orElse(null) : null;
        HostProfile host = hostProfileRepository.findById(account.getId()).orElse(null);
        return UserResponse.builder()
                .id(account.getId())
                .email(account.getEmail())
                .fullName(displayName(account, activeMode))
                .phone(phone(account))
                .contactName(company == null ? null : company.getContactName())
                .companyName(company == null ? null : company.getCompanyName())
                .registrationNumber(company == null ? null : company.getRegistrationNumber())
                .profileCompleted(profileCompleted(account, activeMode))
                .emailVerified(account.isEmailVerified())
                .hostStatus(host == null ? null : host.getVerificationStatus())
                .role(activeMode.role())
                .accountType(account.getAccountType())
                .roles(new LinkedHashSet<>(account.getRoles()))
                .allowedModes(allowedModes)
                .defaultMode(defaultMode(account))
                .enabled(account.isEnabled())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String displayName(Account account, AccessMode mode) {
        if (account.getAccountType() == AccountType.COMPANY) {
            return companyRepository.findByAccount_Id(account.getId())
                    .map(company -> hasText(company.getCompanyName()) ? company.getCompanyName() : company.getContactName())
                    .filter(this::hasText)
                    .orElse(account.getEmail());
        }
        if (mode == AccessMode.HOST) {
            return hostProfileRepository.findById(account.getId())
                    .map(HostProfile::getDisplayName)
                    .orElse(account.getEmail());
        }
        return evUserProfileRepository.findById(account.getId())
                .map(EvUserProfile::getFullName)
                .or(() -> hostProfileRepository.findById(account.getId()).map(HostProfile::getDisplayName))
                .orElse(account.getEmail());
    }

    private String phone(Account account) {
        if (account.getAccountType() == AccountType.COMPANY) {
            return companyRepository.findByAccount_Id(account.getId()).map(Company::getSupportPhone).orElse(null);
        }
        return evUserProfileRepository.findById(account.getId()).map(EvUserProfile::getPhone)
                .or(() -> hostProfileRepository.findById(account.getId()).map(HostProfile::getPhone))
                .orElse(null);
    }

    private boolean profileCompleted(Account account, AccessMode mode) {
        if (mode == AccessMode.COMPANY) {
            return companyRepository.findByAccount_Id(account.getId())
                    .map(company -> hasText(company.getContactName())
                            && validPhone(company.getSupportPhone())
                            && hasText(company.getCompanyName())
                            && hasText(company.getRegistrationNumber()))
                    .orElse(false);
        }
        if (mode == AccessMode.HOST) {
            return hostProfileRepository.findById(account.getId())
                    .map(profile -> hasText(profile.getDisplayName()) && validPhone(profile.getPhone()))
                    .orElse(false);
        }
        return evUserProfileRepository.findById(account.getId())
                .map(profile -> hasText(profile.getFullName()) && validPhone(profile.getPhone()))
                .orElse(false);
    }

    private AccessMode defaultMode(Account account) {
        if (account.allows(AccessMode.EV_USER)) return AccessMode.EV_USER;
        if (account.allows(AccessMode.HOST)) return AccessMode.HOST;
        if (account.allows(AccessMode.COMPANY)) return AccessMode.COMPANY;
        if (account.allows(AccessMode.ADMIN)) return AccessMode.ADMIN;
        throw new IllegalStateException("Account has no valid access mode");
    }

    private Account findByEmail(String email) {
        return accountRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean validPhone(String value) {
        return value != null && value.matches("^[0-9]{10}$");
    }

    @SuppressWarnings("unchecked")
    private GoogleIdentity verifyGoogleIdentity(String accessToken) {
        try {
            Map<String, Object> tokenInfo = googleClient.get()
                    .uri("https://www.googleapis.com/oauth2/v2/tokeninfo?access_token={token}", accessToken)
                    .retrieve()
                    .body(Map.class);
            if (tokenInfo == null) throw new UnauthorizedException("Google could not verify this sign-in");

            String audience = stringValue(tokenInfo.get("audience"));
            if (!hasText(audience)) audience = stringValue(tokenInfo.get("aud"));
            if (!hasText(audience)) audience = stringValue(tokenInfo.get("issued_to"));
            boolean audienceAllowed = !hasText(googleClientIds) || java.util.Arrays.stream(googleClientIds.split(","))
                    .map(String::trim).filter(this::hasText).anyMatch(audience::equals);
            if (!audienceAllowed) {
                throw new UnauthorizedException("Google token was not issued for Vidyut");
            }
            if (!truthy(tokenInfo.get("verified_email")) && !truthy(tokenInfo.get("email_verified"))) {
                throw new UnauthorizedException("A verified Google email is required");
            }
            String tokenEmail = normalizeEmail(stringValue(tokenInfo.get("email")));
            String tokenSubject = stringValue(tokenInfo.get("user_id"));

            Map<String, Object> userInfo = googleClient.get()
                    .uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            if (userInfo == null) throw new UnauthorizedException("Google account information is unavailable");

            String subject = stringValue(userInfo.get("sub"));
            String email = normalizeEmail(stringValue(userInfo.get("email")));
            String name = stringValue(userInfo.get("name"));
            if (!hasText(subject) || !hasText(email) || !truthy(userInfo.get("email_verified"))) {
                throw new UnauthorizedException("Google returned an incomplete identity");
            }
            if ((hasText(tokenEmail) && !tokenEmail.equals(email))
                    || (hasText(tokenSubject) && !tokenSubject.equals(subject))) {
                throw new UnauthorizedException("Google identity verification did not match the selected account");
            }
            if (!hasText(name)) name = email.substring(0, email.indexOf('@'));
            return new GoogleIdentity(subject, email, name.trim());
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new UnauthorizedException("Google sign-in could not be verified");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean truthy(Object value) {
        return value instanceof Boolean bool ? bool : "true".equalsIgnoreCase(stringValue(value));
    }

    private static RestClient createGoogleClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private record GoogleIdentity(String subject, String email, String name) {}
}
