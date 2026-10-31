package com.rideplatform.auth.service.impl;

import com.rideplatform.auth.domain.entity.User;
import com.rideplatform.auth.domain.enums.UserStatus;
import com.rideplatform.auth.dto.AuthResponse;
import com.rideplatform.auth.dto.LoginRequest;
import com.rideplatform.auth.dto.RegisterRequest;
import com.rideplatform.auth.dto.UserResponse;
import com.rideplatform.auth.exception.InvalidCredentialsException;
import com.rideplatform.auth.exception.UserAlreadyExistsException;
import com.rideplatform.auth.repository.UserRepository;
import com.rideplatform.auth.security.JwtTokenProvider;
import com.rideplatform.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            throw new UserAlreadyExistsException("A user with email " + request.getEmail() + " already exists");
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Successfully registered user with ID: {}", savedUser.getId());

        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        log.info("Authenticating user with email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password attempt for email: {}", email);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login attempt for inactive user ID: {}", user.getId());
            throw new InvalidCredentialsException("Account is " + user.getStatus());
        }

        String token = jwtTokenProvider.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
                .user(UserResponse.fromEntity(user))
                .build();
    }
}
