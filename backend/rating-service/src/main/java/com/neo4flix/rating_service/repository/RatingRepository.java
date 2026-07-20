package com.neo4flix.rating_service.repository;

import com.neo4flix.rating_service.dto.MovieRatingView;
import com.neo4flix.rating_service.dto.OwnRatingView;
import com.neo4flix.rating_service.dto.UserRatingView;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Raw Cypher via Neo4jClient - no OGM entities for User/Movie here. This service only
// touches the RATED relationship, plus Movie.averageRating (the one field it's allowed to write).
@Repository
public class RatingRepository {

    private final Neo4jClient neo4jClient;

    public RatingRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public boolean movieExists(String movieId) {
        return exists("MATCH (m:Movie {movieId: $movieId}) RETURN count(m) AS total", "movieId", movieId);
    }

    public boolean userExists(String userId) {
        return exists("MATCH (u:User {userId: $userId}) RETURN count(u) AS total", "userId", userId);
    }

    private boolean exists(String cypher, String paramName, String paramValue) {
        return neo4jClient.query(cypher)
                .bind(paramValue).to(paramName)
                .fetch()
                .one()
                .map(row -> ((Number) row.get("total")).longValue() > 0)
                .orElse(false);
    }

    public void upsertRating(String userId, String movieId, double score, String comment) {
        neo4jClient.query("""
                        MATCH (u:User {userId: $userId})
                        MATCH (m:Movie {movieId: $movieId})
                        MERGE (u)-[r:RATED]->(m)
                        SET r.score = $score, r.comment = $comment, r.ratedAt = datetime()
                        WITH m
                        MATCH (:User)-[rt:RATED]->(m)
                        WITH m, avg(rt.score) AS avgScore
                        SET m.averageRating = avgScore
                        """)
                .bind(userId).to("userId")
                .bind(movieId).to("movieId")
                .bind(score).to("score")
                .bind(comment == null ? "" : comment).to("comment")
                .run();
    }

    public void deleteRating(String userId, String movieId) {
        neo4jClient.query("""
                        MATCH (:User {userId: $userId})-[r:RATED]->(m:Movie {movieId: $movieId})
                        DELETE r
                        WITH m
                        OPTIONAL MATCH (:User)-[rt:RATED]->(m)
                        WITH m, avg(rt.score) AS avgScore
                        SET m.averageRating = coalesce(avgScore, 0.0)
                        """)
                .bind(userId).to("userId")
                .bind(movieId).to("movieId")
                .run();
    }

    public Optional<OwnRatingView> findOwnRating(String userId, String movieId) {
        return neo4jClient.query("""
                        MATCH (:User {userId: $userId})-[r:RATED]->(:Movie {movieId: $movieId})
                        RETURN r.score AS score, r.comment AS comment, toString(r.ratedAt) AS ratedAt
                        """)
                .bind(userId).to("userId")
                .bind(movieId).to("movieId")
                .fetch()
                .one()
                .map(row -> new OwnRatingView(
                        ((Number) row.get("score")).doubleValue(),
                        (String) row.get("comment"),
                        (String) row.get("ratedAt")));
    }

    public List<MovieRatingView> findRatingsForMovie(String movieId) {
        return neo4jClient.query("""
                        MATCH (u:User)-[r:RATED]->(m:Movie {movieId: $movieId})
                        RETURN u.userId AS userId, u.username AS username, r.score AS score,
                               r.comment AS comment, toString(r.ratedAt) AS ratedAt
                        ORDER BY r.ratedAt DESC
                        """)
                .bind(movieId).to("movieId")
                .fetch()
                .all()
                .stream()
                .map(row -> new MovieRatingView(
                        (String) row.get("userId"),
                        (String) row.get("username"),
                        ((Number) row.get("score")).doubleValue(),
                        (String) row.get("comment"),
                        (String) row.get("ratedAt")))
                .toList();
    }

    public List<UserRatingView> findRatingsByUser(String userId) {
        return neo4jClient.query("""
                        MATCH (:User {userId: $userId})-[r:RATED]->(m:Movie)
                        RETURN m.movieId AS movieId, m.title AS movieTitle, r.score AS score,
                               r.comment AS comment, toString(r.ratedAt) AS ratedAt
                        ORDER BY r.ratedAt DESC
                        """)
                .bind(userId).to("userId")
                .fetch()
                .all()
                .stream()
                .map(row -> new UserRatingView(
                        (String) row.get("movieId"),
                        (String) row.get("movieTitle"),
                        ((Number) row.get("score")).doubleValue(),
                        (String) row.get("comment"),
                        (String) row.get("ratedAt")))
                .toList();
    }
}
