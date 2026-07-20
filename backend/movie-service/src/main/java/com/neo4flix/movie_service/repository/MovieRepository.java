package com.neo4flix.movie_service.repository;

import com.neo4flix.movie_service.domain.Movie;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends Neo4jRepository<Movie, String> {

    Optional<Movie> findByTitle(String title);

    List<Movie> findByReleaseDateBetween(LocalDate from, LocalDate to);

    @Query("""
            MATCH (m:Movie)-[:IN_GENRE]->(g:Genre {name: $genreName})
            RETURN m
            """)
    List<Movie> findByGenreName(@Param("genreName") String genreName);

    // Backed by the movieSearchIndex fulltext index (see schema.cypher)
    @Query("""
            CALL db.index.fulltext.queryNodes('movieSearchIndex', $searchTerm) YIELD node, score
            RETURN node
            ORDER BY score DESC
            """)
    List<Movie> searchByTitleOrSynopsis(@Param("searchTerm") String searchTerm);
}
