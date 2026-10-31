package com.rideplatform.auth.service;

import com.rideplatform.auth.dto.UserResponse;

import java.util.UUID;

public interface UserService {
    UserResponse getUserById(UUID id);
    UserResponse getUserByEmail(String email);
}
