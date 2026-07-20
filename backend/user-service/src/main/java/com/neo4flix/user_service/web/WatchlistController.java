package com.neo4flix.user_service.web;

import com.neo4flix.user_service.dto.WatchlistItem;
import com.neo4flix.user_service.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users/me/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItem> list(Principal principal) {
        return watchlistService.getWatchlist(principal.getName());
    }

    @PostMapping("/{movieId}")
    public ResponseEntity<Void> add(Principal principal, @PathVariable String movieId) {
        watchlistService.addToWatchlist(principal.getName(), movieId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> remove(Principal principal, @PathVariable String movieId) {
        watchlistService.removeFromWatchlist(principal.getName(), movieId);
        return ResponseEntity.noContent().build();
    }
}
