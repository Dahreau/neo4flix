package com.neo4flix.user_service.dto;

// auth is null when requiresTwoFactor is true - client must call /api/auth/2fa/verify-login next.
public record LoginResponse(boolean requiresTwoFactor, AuthResponse auth) {
}
