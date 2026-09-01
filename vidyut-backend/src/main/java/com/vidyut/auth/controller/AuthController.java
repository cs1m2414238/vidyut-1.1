package com.vidyut.auth.controller;

import com.vidyut.auth.dto.*;
import com.vidyut.auth.service.AuthService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserUtil currentUser;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> google(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Google sign-in successful",
                authService.authenticateWithGoogle(request)));
    }

    @PostMapping("/register/user")
    public ResponseEntity<ApiResponse<AuthResponse>> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Registration successful", authService.registerUser(request)));
    }

    @PostMapping("/register/host")
    public ResponseEntity<ApiResponse<AuthResponse>> registerHost(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Host registration successful", authService.registerHost(request)));
    }

    @PostMapping("/register/company")
    public ResponseEntity<ApiResponse<AuthResponse>> registerCompany(@Valid @RequestBody RegisterCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company registration successful", authService.registerCompany(request)));
    }

    @PostMapping("/switch-mode")
    public ResponseEntity<ApiResponse<AuthResponse>> switchMode(@Valid @RequestBody SwitchModeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Mode switched successfully",
                authService.switchMode(currentUser.getCurrentUserEmail(), request.getMode())));
    }

    @PostMapping("/host/apply")
    public ResponseEntity<ApiResponse<UserResponse>> applyForHost(@RequestBody HostApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Host application submitted for approval",
                authService.applyForHost(currentUser.getCurrentUserEmail(), request.getDisplayName())));
    }

    @PutMapping("/complete-profile")
    public ResponseEntity<ApiResponse<AuthResponse>> completeProfile(
            @Valid @RequestBody CompleteProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile completed",
                authService.completeProfile(currentUser.getCurrentUserEmail(), request)));
    }
}
