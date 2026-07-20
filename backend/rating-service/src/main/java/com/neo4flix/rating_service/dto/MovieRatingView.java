package com.neo4flix.rating_service.dto;

// Rating as seen from a movie page - who rated it, no need for the movie title.
public record MovieRatingView(String userId, String username, Double score, String comment, String ratedAt) {
}
