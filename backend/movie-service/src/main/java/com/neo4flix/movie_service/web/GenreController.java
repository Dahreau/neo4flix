package com.neo4flix.movie_service.web;

import com.neo4flix.movie_service.domain.Genre;
import com.neo4flix.movie_service.repository.GenreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Feeds the genre filter dropdown on the frontend.
@RestController
@RequestMapping("/api/genres")
public class GenreController {

    private final GenreRepository genreRepository;

    public GenreController(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @GetMapping
    public List<String> list() {
        return genreRepository.findAll().stream().map(Genre::getName).toList();
    }
}
