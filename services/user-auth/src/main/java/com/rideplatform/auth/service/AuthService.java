package com.rideplatform.auth.service;

import com.rideplatform.auth.dto.AuthResponse;
import com.rideplatform.auth.dto.LoginRequest;
import com.rideplatform.auth.dto.RegisterRequest;
import com.rideplatform.auth.dto.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
