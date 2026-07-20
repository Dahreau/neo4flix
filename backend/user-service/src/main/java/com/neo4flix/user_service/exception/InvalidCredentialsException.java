package com.neo4flix.user_service.exception;

// Same message whether the username or the password is wrong - don't leak account existence.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Identifiants invalides");
    }
}
