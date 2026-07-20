// =========================================================
// Neo4flix - graph schema (constraints + indexes)
// Run once after the neo4j container is up.
// =========================================================

// --- Uniqueness constraints (also create an index) ---
CREATE CONSTRAINT movie_id_unique IF NOT EXISTS
FOR (m:Movie) REQUIRE m.movieId IS UNIQUE;

CREATE CONSTRAINT genre_name_unique IF NOT EXISTS
FOR (g:Genre) REQUIRE g.name IS UNIQUE;

CREATE CONSTRAINT person_id_unique IF NOT EXISTS
FOR (p:Person) REQUIRE p.personId IS UNIQUE;

CREATE CONSTRAINT user_id_unique IF NOT EXISTS
FOR (u:User) REQUIRE u.userId IS UNIQUE;

CREATE CONSTRAINT user_username_unique IF NOT EXISTS
FOR (u:User) REQUIRE u.username IS UNIQUE;

CREATE CONSTRAINT user_email_unique IF NOT EXISTS
FOR (u:User) REQUIRE u.email IS UNIQUE;

// --- Search indexes (filter criteria) ---
CREATE INDEX movie_release_date_index IF NOT EXISTS
FOR (m:Movie) ON (m.releaseDate);

CREATE INDEX person_name_index IF NOT EXISTS
FOR (p:Person) ON (p.name);

// --- Fulltext index for free-text search (title + synopsis) ---
CREATE FULLTEXT INDEX movieSearchIndex IF NOT EXISTS
FOR (m:Movie) ON EACH [m.title, m.originalTitle, m.synopsis];
