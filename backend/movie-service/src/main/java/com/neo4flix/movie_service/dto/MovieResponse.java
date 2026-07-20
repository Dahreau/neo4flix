package com.neo4flix.movie_service.dto;

import java.time.LocalDate;
import java.util.Set;

public record MovieResponse(
        String movieId,
        String title,
        String originalTitle,
        LocalDate releaseDate,
        Integer durationMinutes,
        String synopsis,
        String posterUrl,
        Double averageRating,
        Set<String> genres
) {
}
