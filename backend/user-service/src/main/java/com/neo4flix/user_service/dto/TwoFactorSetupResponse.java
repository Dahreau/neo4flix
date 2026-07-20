package com.neo4flix.user_service.dto;

// secret is also returned for manual entry, in case the user can't scan the QR code.
public record TwoFactorSetupResponse(String secret, String qrCodeDataUri) {
}
