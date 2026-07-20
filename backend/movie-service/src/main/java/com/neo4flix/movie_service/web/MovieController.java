package com.neo4flix.movie_service.web;

import com.neo4flix.movie_service.dto.MovieRequest;
import com.neo4flix.movie_service.dto.MovieResponse;
import com.neo4flix.movie_service.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<MovieResponse> list() {
        return movieService.list();
    }

    @GetMapping("/{movieId}")
    public MovieResponse get(@PathVariable String movieId) {
        return movieService.get(movieId);
    }

    @GetMapping("/search")
    public List<MovieResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return movieService.search(q, genre, from, to);
    }

    @PostMapping
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.create(request));
    }

    @PutMapping("/{movieId}")
    public MovieResponse update(@PathVariable String movieId, @Valid @RequestBody MovieRequest request) {
        return movieService.update(movieId, request);
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> delete(@PathVariable String movieId) {
        movieService.delete(movieId);
        return ResponseEntity.noContent().build();
    }
}
