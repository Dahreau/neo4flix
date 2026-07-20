package com.neo4flix.user_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Password strength (length/case/digit mix) is checked separately in AuthService,
// not here - that rule is about content, not just presence, and needs a custom
// message the client already relies on (see WeakPasswordException).
public record RegisterRequest(
        @NotBlank(message = "Le nom d'utilisateur est requis") String username,
        @NotBlank(message = "L'email est requis") @Email(message = "Email invalide") String email,
        @NotBlank(message = "Le mot de passe est requis") String password
) {
}
