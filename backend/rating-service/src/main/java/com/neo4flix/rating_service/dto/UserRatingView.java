package com.neo4flix.rating_service.dto;

// Rating as seen from a user profile - which movie, no need for the username.
public record UserRatingView(String movieId, String movieTitle, Double score, String comment, String ratedAt) {
}
