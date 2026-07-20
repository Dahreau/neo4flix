package com.neo4flix.user_service.dto;

public record TwoFactorLoginRequest(String username, String code) {
}
