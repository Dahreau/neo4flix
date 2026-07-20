package com.neo4flix.recommendation_service.dto;

// relevanceScore's meaning depends on the strategy: shared genre count (content-based),
// predicted average score from similar users (collaborative / similar-users).
public record MovieRecommendation(
        String movieId,
        String title,
        String posterUrl,
        Double averageRating,
        Double relevanceScore
) {
}
