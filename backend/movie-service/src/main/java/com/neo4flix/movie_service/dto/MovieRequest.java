package com.neo4flix.movie_service.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Set;

// Never bind the Neo4j entity directly on the controller - keeps the OGM mapping off the wire.
// Only title is required - the rest are genuinely optional catalogue metadata.
public record MovieRequest(
        @NotBlank(message = "Le titre est requis") String title,
        String originalTitle,
        LocalDate releaseDate,
        Integer durationMinutes,
        String synopsis,
        String posterUrl,
        Set<String> genres
) {
}
