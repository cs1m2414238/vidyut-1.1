package com.vidyut.admin.controller;

import com.vidyut.admin.dto.SupportCaseCreateRequest;
import com.vidyut.admin.entity.AdminSupportCase;
import com.vidyut.admin.service.AdminControlService;
import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support/cases")
@RequiredArgsConstructor
public class SupportCaseController {
    private final AdminControlService controlService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminSupportCase>> create(@Valid @RequestBody SupportCaseCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Support case created", controlService.createSupportCase(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminSupportCase>>> mine() {
        return ResponseEntity.ok(ApiResponse.success(controlService.mySupportCases(currentUser.getCurrentAccountId())));
    }
}
