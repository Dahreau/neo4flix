package com.neo4flix.rating_service.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("Utilisateur introuvable : " + userId);
    }
}
