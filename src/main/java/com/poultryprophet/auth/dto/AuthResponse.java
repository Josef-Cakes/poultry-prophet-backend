package com.poultryprophet.auth.dto;

import com.poultryprophet.user.Role;

import java.time.Instant;

public record AuthResponse(
        String token,
        Long userId,
        String email,
        String fullName,
        Role role,
        Long farmId,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
