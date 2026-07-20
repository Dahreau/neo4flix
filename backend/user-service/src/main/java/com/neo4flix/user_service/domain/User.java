package com.neo4flix.user_service.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

// No @Relationship to Movie here - watchlist writes go through WatchlistRepository (raw Cypher)
// so a User save() can never cascade into a Movie node owned by movie-service.
@Node("User")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "userId")
public class User {

    @Id
    @GeneratedValue(UUIDStringGenerator.class)
    private String userId;

    private String username;

    private String email;

    private String passwordHash;

    @Builder.Default
    private Set<String> roles = new HashSet<>(Set.of("ROLE_USER"));

    private Instant createdAt;

    // Null until /api/auth/2fa/setup is called.
    private String totpSecret;

    @Builder.Default
    private boolean twoFactorEnabled = false;
}
