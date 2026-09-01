package com.vidyut.auth.dto;

import com.vidyut.user.dto.UserResponse;
import com.vidyut.account.entity.AccessMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private AccessMode activeMode;
    private UserResponse user;
}
