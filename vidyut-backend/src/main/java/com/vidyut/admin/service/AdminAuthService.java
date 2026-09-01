package com.vidyut.admin.service;

import com.vidyut.account.entity.AccessMode;
import com.vidyut.account.entity.Account;
import com.vidyut.account.entity.AccountType;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.admin.dto.AdminLoginResponse;
import com.vidyut.admin.dto.AdminProfileResponse;
import com.vidyut.admin.entity.AdminAccount;
import com.vidyut.admin.repository.AdminAccountRepository;
import com.vidyut.auth.dto.LoginRequest;
import com.vidyut.common.exception.UnauthorizedException;
import com.vidyut.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final AdminAccountRepository adminRepository;
    private final AdminAccessService accessService;
    private final JwtService jwtService;

    @Transactional
    public AdminLoginResponse login(LoginRequest request) {
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid administrator email or password");
        }
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .filter(item -> item.getAccountType() == AccountType.ADMIN && item.allows(AccessMode.ADMIN))
                .orElseThrow(() -> new UnauthorizedException("This account is not a Vidyut administrator"));
        AdminAccount admin = adminRepository.findById(account.getId())
                .filter(AdminAccount::isActive)
                .orElseThrow(() -> new UnauthorizedException("Administrator access is inactive"));
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);
        AdminProfileResponse profile = accessService.profile(admin);
        return new AdminLoginResponse(jwtService.generateModeToken(account, AccessMode.ADMIN), profile);
    }

    public AdminProfileResponse me() {
        return accessService.profile(accessService.currentAdmin());
    }
}
