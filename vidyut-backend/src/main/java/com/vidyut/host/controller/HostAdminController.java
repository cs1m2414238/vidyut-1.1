package com.vidyut.host.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.host.dto.HostProfileResponse;
import com.vidyut.host.service.HostOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/hosts")
@RequiredArgsConstructor
public class HostAdminController {
    private final HostOperationsService hostService;

    @PostMapping("/{accountId}/verify")
    public ResponseEntity<ApiResponse<HostProfileResponse>> verify(@PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success("Host KYC approved", hostService.approveVerification(accountId)));
    }
}
