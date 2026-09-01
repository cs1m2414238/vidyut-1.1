package com.vidyut.user.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.user.dto.UpdateUserRequest;
import com.vidyut.user.dto.UserResponse;
import com.vidyut.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CurrentUserUtil currentUser;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentAccount() {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(currentUser.getCurrentAccountId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentAccount(@RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(currentUser.getCurrentAccountId(), request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteCurrentAccount() {
        userService.deleteUser(currentUser.getCurrentAccountId());
        return ResponseEntity.ok(ApiResponse.success("Account deleted and personal profile data removed", null));
    }
}
