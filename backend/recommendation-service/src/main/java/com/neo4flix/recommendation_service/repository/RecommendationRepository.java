package com.neo4flix.recommendation_service.repository;

import com.neo4flix.recommendation_service.dto.MovieRecommendation;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Read-only, Neo4jClient throughout - this service never writes to User, Movie or RATED,
// it only traverses relationships owned by other services.
@Repository
public class RecommendationRepository {

    private static final String GDS_GRAPH_NAME = "userMovieGraph";
    private static final double LIKED_THRESHOLD = 4.0;
    private static final int RESULT_LIMIT = 20;

    private final Neo4jClient neo4jClient;

    public RecommendationRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Content-based: movies sharing genres with what this user already rated highly.
     * Pure Cypher graph traversal, no algorithm involved.
     */
    public List<MovieRecommendation> contentBased(String userId, String genre, LocalDate from, LocalDate to) {
        return neo4jClient.query("""
                        MATCH (me:User {userId: $userId})-[r:RATED]->(liked:Movie)-[:IN_GENRE]->(g:Genre)<-[:IN_GENRE]-(rec:Movie)
                        WHERE r.score >= $likedThreshold
                          AND rec <> liked
                          AND NOT (me)-[:RATED]->(rec)
                          AND ($genre IS NULL OR (rec)-[:IN_GENRE]->(:Genre {name: $genre}))
                          AND ($from IS NULL OR rec.releaseDate >= $from)
                          AND ($to IS NULL OR rec.releaseDate <= $to)
                        RETURN rec.movieId AS movieId, rec.title AS title, rec.posterUrl AS posterUrl,
                               rec.averageRating AS averageRating, count(DISTINCT g) AS relevanceScore
                        ORDER BY relevanceScore DESC, rec.averageRating DESC
                        LIMIT $limit
                        """)
                .bind(userId).to("userId")
                .bind(LIKED_THRESHOLD).to("likedThreshold")
                .bind(genre).to("genre")
                .bind(from).to("from")
                .bind(to).to("to")
                .bind(RESULT_LIMIT).to("limit")
                .fetch()
                .all()
                .stream()
                .map(this::toRecommendation)
                .toList();
    }

    /**
     * Collaborative filtering, plain Cypher: movies liked by other users who rated
     * the same movies as this one. No GDS call, just a graph traversal.
     */
    public List<MovieRecommendation> collaborative(String userId, String genre, LocalDate from, LocalDate to) {
        return neo4jClient.query("""
                        MATCH (me:User {userId: $userId})-[:RATED]->(:Movie)<-[:RATED]-(peer:User)
                        WHERE peer <> me
                        MATCH (peer)-[r:RATED]->(rec:Movie)
                        WHERE NOT (me)-[:RATED]->(rec)
                          AND ($genre IS NULL OR (rec)-[:IN_GENRE]->(:Genre {name: $genre}))
                          AND ($from IS NULL OR rec.releaseDate >= $from)
                          AND ($to IS NULL OR rec.releaseDate <= $to)
                        RETURN rec.movieId AS movieId, rec.title AS title, rec.posterUrl AS posterUrl,
                               rec.averageRating AS averageRating, avg(r.score) AS relevanceScore
                        ORDER BY relevanceScore DESC
                        LIMIT $limit
                        """)
                .bind(userId).to("userId")
                .bind(genre).to("genre")
                .bind(from).to("from")
                .bind(to).to("to")
                .bind(RESULT_LIMIT).to("limit")
                .fetch()
                .all()
                .stream()
                .map(this::toRecommendation)
                .toList();
    }

    /**
     * Collaborative filtering via Neo4j GDS Node Similarity. Projects a temporary
     * User-Movie graph, finds the closest users by shared-rating overlap (Jaccard),
     * then recommends what those users liked. Re-projects on every call to stay
     * correct against fresh ratings - fine for this project's scale, would move to
     * a scheduled refresh for a real deployment.
     */
    public List<MovieRecommendation> similarUsers(String userId, String genre, LocalDate from, LocalDate to) {
        dropGdsGraphIfPresent();
        projectGdsGraph();

        List<String> similarUserIds = findSimilarUserIds(userId);

        dropGdsGraphIfPresent();

        if (similarUserIds.isEmpty()) {
            return List.of();
        }

        return moviesRatedByUsers(userId, similarUserIds, genre, from, to);
    }

    private void dropGdsGraphIfPresent() {
        // failIfMissing=false makes this a no-op instead of throwing when the graph isn't projected yet.
        neo4jClient.query("CALL gds.graph.drop($graphName, false)")
                .bind(GDS_GRAPH_NAME).to("graphName")
                .run();
    }

    private void projectGdsGraph() {
        neo4jClient.query("""
                        CALL gds.graph.project($graphName, ['User', 'Movie'], {
                            RATED: { orientation: 'UNDIRECTED' }
                        })
                        """)
                .bind(GDS_GRAPH_NAME).to("graphName")
                .run();
    }

    private List<String> findSimilarUserIds(String userId) {
        return neo4jClient.query("""
                        CALL gds.nodeSimilarity.filtered.stream($graphName, {
                            sourceNodeFilter: 'User', targetNodeFilter: 'User'
                        })
                        YIELD node1, node2, similarity
                        WITH gds.util.asNode(node1) AS n1, gds.util.asNode(node2) AS n2, similarity
                        WHERE n1.userId = $userId
                        RETURN n2.userId AS similarUserId
                        ORDER BY similarity DESC
                        LIMIT 10
                        """)
                .bind(GDS_GRAPH_NAME).to("graphName")
                .bind(userId).to("userId")
                .fetch()
                .all()
                .stream()
                .map(row -> (String) row.get("similarUserId"))
                .toList();
    }

    private List<MovieRecommendation> moviesRatedByUsers(String userId, List<String> similarUserIds,
                                                           String genre, LocalDate from, LocalDate to) {
        return neo4jClient.query("""
                        MATCH (peer:User) WHERE peer.userId IN $similarUserIds
                        MATCH (peer)-[r:RATED]->(rec:Movie)
                        WHERE NOT (:User {userId: $userId})-[:RATED]->(rec)
                          AND ($genre IS NULL OR (rec)-[:IN_GENRE]->(:Genre {name: $genre}))
                          AND ($from IS NULL OR rec.releaseDate >= $from)
                          AND ($to IS NULL OR rec.releaseDate <= $to)
                        RETURN rec.movieId AS movieId, rec.title AS title, rec.posterUrl AS posterUrl,
                               rec.averageRating AS averageRating, avg(r.score) AS relevanceScore
                        ORDER BY relevanceScore DESC
                        LIMIT $limit
                        """)
                .bind(userId).to("userId")
                .bind(similarUserIds).to("similarUserIds")
                .bind(genre).to("genre")
                .bind(from).to("from")
                .bind(to).to("to")
                .bind(RESULT_LIMIT).to("limit")
                .fetch()
                .all()
                .stream()
                .map(this::toRecommendation)
                .toList();
    }

    private MovieRecommendation toRecommendation(Map<String, Object> row) {
        return new MovieRecommendation(
                (String) row.get("movieId"),
                (String) row.get("title"),
                (String) row.get("posterUrl"),
                row.get("averageRating") == null ? null : ((Number) row.get("averageRating")).doubleValue(),
                row.get("relevanceScore") == null ? null : ((Number) row.get("relevanceScore")).doubleValue()
        );
    }
}
