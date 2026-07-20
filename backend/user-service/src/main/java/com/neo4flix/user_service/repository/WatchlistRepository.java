package com.neo4flix.user_service.repository;

import com.neo4flix.user_service.dto.WatchlistItem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

// Raw Cypher via Neo4jClient instead of an OGM relationship on User - a User aggregate
// save() must never risk touching a Movie node's properties (owned by movie-service).
@Repository
public class WatchlistRepository {

    private final Neo4jClient neo4jClient;

    public WatchlistRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public boolean movieExists(String movieId) {
        return neo4jClient.query("MATCH (m:Movie {movieId: $movieId}) RETURN count(m) AS total")
                .bind(movieId).to("movieId")
                .fetch()
                .one()
                .map(row -> ((Number) row.get("total")).longValue() > 0)
                .orElse(false);
    }

    public void add(String userId, String movieId) {
        neo4jClient.query("""
                        MATCH (u:User {userId: $userId})
                        MATCH (m:Movie {movieId: $movieId})
                        MERGE (u)-[r:WANTS_TO_WATCH]->(m)
                        ON CREATE SET r.addedAt = datetime()
                        """)
                .bind(userId).to("userId")
                .bind(movieId).to("movieId")
                .run();
    }

    public void remove(String userId, String movieId) {
        neo4jClient.query("""
                        MATCH (:User {userId: $userId})-[r:WANTS_TO_WATCH]->(:Movie {movieId: $movieId})
                        DELETE r
                        """)
                .bind(userId).to("userId")
                .bind(movieId).to("movieId")
                .run();
    }

    public List<WatchlistItem> findAll(String userId) {
        return neo4jClient.query("""
                        MATCH (:User {userId: $userId})-[r:WANTS_TO_WATCH]->(m:Movie)
                        RETURN m.movieId AS movieId, m.title AS title, m.posterUrl AS posterUrl,
                               toString(r.addedAt) AS addedAt
                        ORDER BY r.addedAt DESC
                        """)
                .bind(userId).to("userId")
                .fetch()
                .all()
                .stream()
                .map(this::toWatchlistItem)
                .toList();
    }

    private WatchlistItem toWatchlistItem(Map<String, Object> row) {
        return new WatchlistItem(
                (String) row.get("movieId"),
                (String) row.get("title"),
                (String) row.get("posterUrl"),
                (String) row.get("addedAt")
        );
    }
}
