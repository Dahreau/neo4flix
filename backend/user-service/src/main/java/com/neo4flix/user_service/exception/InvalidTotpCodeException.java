package com.neo4flix.user_service.exception;

public class InvalidTotpCodeException extends RuntimeException {

    public InvalidTotpCodeException() {
        super("Code d'authentification invalide ou expire");
    }
}
