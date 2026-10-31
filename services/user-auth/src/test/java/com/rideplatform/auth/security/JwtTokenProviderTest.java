package com.rideplatform.auth.security;

import com.rideplatform.auth.domain.entity.User;
import com.rideplatform.auth.domain.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLXJpZGUtc2hhcmluZy1wbGF0Zm9ybQ==";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    @DisplayName("Should generate valid JWT token and extract user ID")
    void shouldGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .fullName("Test User")
                .role(Role.RIDER)
                .build();

        String token = jwtTokenProvider.generateToken(user);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        boolean isValid = jwtTokenProvider.validateToken(token);
        assertTrue(isValid);

        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(userId, extractedUserId);
    }

    @Test
    @DisplayName("Should reject malformed or tampered token")
    void shouldRejectInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }
}
