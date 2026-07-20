package com.neo4flix.rating_service.exception;

public class RatingNotFoundException extends RuntimeException {

    public RatingNotFoundException(String userId, String movieId) {
        super("Aucune note de " + userId + " pour le film " + movieId);
    }
}
