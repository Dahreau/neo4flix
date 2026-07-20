package com.neo4flix.user_service.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(String userId, String username, String email, Set<String> roles, Instant createdAt) {
}
