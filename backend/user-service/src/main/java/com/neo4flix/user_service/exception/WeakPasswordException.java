package com.neo4flix.user_service.exception;

public class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("Le mot de passe doit faire au moins 8 caracteres et contenir une majuscule, une minuscule et un chiffre.");
    }
}
