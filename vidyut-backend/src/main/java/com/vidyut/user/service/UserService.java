package com.vidyut.user.service;

import com.vidyut.user.dto.UpdateUserRequest;
import com.vidyut.user.dto.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse getUserById(Long id);
    UserResponse getUserByEmail(String email);
    List<UserResponse> getAllUsers();
    UserResponse updateUser(Long id, UpdateUserRequest request);
    void deleteUser(Long id);
}
