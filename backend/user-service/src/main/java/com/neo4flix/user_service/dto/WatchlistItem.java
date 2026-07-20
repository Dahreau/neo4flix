package com.neo4flix.user_service.dto;

public record WatchlistItem(
        String movieId,
        String title,
        String posterUrl,
        String addedAt
) {
}
