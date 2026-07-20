package com.neo4flix.movie_service.config;

// Superseded by security/SecurityConfig.java's corsConfigurationSource() bean, now that
// movie-service has a Spring Security filter chain (WebMvcConfigurer-based CORS would be
// bypassed by the security filter chain on protected routes anyway - see docs/01-architecture.md).
// Left as a placeholder rather than deleted: this file's path couldn't be removed in the
// environment this was written in. Safe to actually delete this file during a normal edit.
final class CorsConfig {
    private CorsConfig() {
    }
}
