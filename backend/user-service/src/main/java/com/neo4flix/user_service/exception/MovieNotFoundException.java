package com.neo4flix.user_service.exception;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(String movieId) {
        super("Movie introuvable : " + movieId);
    }
}
