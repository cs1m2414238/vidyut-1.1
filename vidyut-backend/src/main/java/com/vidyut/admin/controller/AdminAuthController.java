package com.vidyut.admin.controller;

import com.vidyut.admin.dto.AdminLoginResponse;
import com.vidyut.admin.dto.AdminProfileResponse;
import com.vidyut.admin.service.AdminAuthService;
import com.vidyut.auth.dto.LoginRequest;
import com.vidyut.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Administrator signed in", authService.login(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminProfileResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(authService.me()));
    }
}
