package com.neo4flix.user_service.service;

import com.neo4flix.user_service.dto.WatchlistItem;
import com.neo4flix.user_service.exception.MovieNotFoundException;
import com.neo4flix.user_service.repository.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    public WatchlistService(WatchlistRepository watchlistRepository) {
        this.watchlistRepository = watchlistRepository;
    }

    public void addToWatchlist(String userId, String movieId) {
        if (!watchlistRepository.movieExists(movieId)) {
            throw new MovieNotFoundException(movieId);
        }
        watchlistRepository.add(userId, movieId);
    }

    public void removeFromWatchlist(String userId, String movieId) {
        watchlistRepository.remove(userId, movieId);
    }

    public List<WatchlistItem> getWatchlist(String userId) {
        return watchlistRepository.findAll(userId);
    }
}
