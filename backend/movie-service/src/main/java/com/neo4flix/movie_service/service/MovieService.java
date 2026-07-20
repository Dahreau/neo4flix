package com.neo4flix.movie_service.service;

import com.neo4flix.movie_service.domain.Genre;
import com.neo4flix.movie_service.domain.Movie;
import com.neo4flix.movie_service.dto.MovieRequest;
import com.neo4flix.movie_service.dto.MovieResponse;
import com.neo4flix.movie_service.exception.MovieNotFoundException;
import com.neo4flix.movie_service.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class MovieService {

    // Characters with special meaning to Lucene's query parser (the fulltext index
    // is backed by Lucene under the hood). Left unescaped, a search string like
    // "sci-fi (2020" can throw a query parse exception - not a security hole (the
    // $searchTerm parameter binding already prevents Cypher injection), but a crash
    // on ordinary-looking user input, which is exactly the kind of thing
    // scripts/security-test.ps1 checks for.
    private static final String LUCENE_SPECIAL_CHARS = "+-!(){}[]^\"~*?:\\/&|";

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public MovieResponse create(MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .originalTitle(request.originalTitle())
                .releaseDate(request.releaseDate())
                .durationMinutes(request.durationMinutes())
                .synopsis(request.synopsis())
                .posterUrl(request.posterUrl())
                .createdAt(Instant.now())
                .genres(toGenres(request.genres()))
                .build();

        return toResponse(movieRepository.save(movie));
    }

    public MovieResponse update(String movieId, MovieRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException(movieId));

        movie.setTitle(request.title());
        movie.setOriginalTitle(request.originalTitle());
        movie.setReleaseDate(request.releaseDate());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setSynopsis(request.synopsis());
        movie.setPosterUrl(request.posterUrl());
        movie.setGenres(toGenres(request.genres()));

        return toResponse(movieRepository.save(movie));
    }

    @Transactional(readOnly = true)
    public MovieResponse get(String movieId) {
        return movieRepository.findById(movieId)
                .map(this::toResponse)
                .orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> list() {
        return movieRepository.findAll().stream().map(this::toResponse).toList();
    }

    public void delete(String movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new MovieNotFoundException(movieId);
        }
        movieRepository.deleteById(movieId);
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> search(String query, String genre, LocalDate from, LocalDate to) {
        List<Movie> results;

        if (query != null && !query.isBlank()) {
            results = movieRepository.searchByTitleOrSynopsis(escapeLuceneQuery(query));
        } else if (genre != null && !genre.isBlank()) {
            results = movieRepository.findByGenreName(genre);
        } else if (from != null && to != null) {
            results = movieRepository.findByReleaseDateBetween(from, to);
        } else {
            results = movieRepository.findAll();
        }

        return results.stream().map(this::toResponse).toList();
    }

    private String escapeLuceneQuery(String input) {
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (LUCENE_SPECIAL_CHARS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private Set<Genre> toGenres(Set<String> genreNames) {
        if (genreNames == null) {
            return Set.of();
        }
        return genreNames.stream().map(Genre::of).collect(Collectors.toSet());
    }

    private MovieResponse toResponse(Movie movie) {
        return new MovieResponse(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getOriginalTitle(),
                movie.getReleaseDate(),
                movie.getDurationMinutes(),
                movie.getSynopsis(),
                movie.getPosterUrl(),
                movie.getAverageRating(),
                movie.getGenres().stream().map(Genre::getName).collect(Collectors.toSet())
        );
    }
}
