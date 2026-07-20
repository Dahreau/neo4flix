package com.neo4flix.rating_service.dto;

public record OwnRatingView(Double score, String comment, String ratedAt) {
}
