package com.neo4flix.movie_service.repository;

import com.neo4flix.movie_service.domain.Genre;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface GenreRepository extends Neo4jRepository<Genre, String> {
}
