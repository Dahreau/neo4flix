package com.neo4flix.user_service.dto;

public record AuthResponse(String token, String userId, String username) {
}
