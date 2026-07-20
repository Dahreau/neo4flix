package com.neo4flix.rating_service.web;

import com.neo4flix.rating_service.dto.MovieRatingView;
import com.neo4flix.rating_service.dto.OwnRatingView;
import com.neo4flix.rating_service.dto.RatingRequest;
import com.neo4flix.rating_service.dto.UserRatingView;
import com.neo4flix.rating_service.service.RatingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PutMapping("/api/movies/{movieId}/rating")
    public ResponseEntity<Void> rate(Principal principal, @PathVariable String movieId,
                                      @RequestBody RatingRequest request) {
        ratingService.rate(principal.getName(), movieId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/movies/{movieId}/rating")
    public ResponseEntity<Void> unrate(Principal principal, @PathVariable String movieId) {
        ratingService.unrate(principal.getName(), movieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/movies/{movieId}/rating")
    public OwnRatingView getOwnRating(Principal principal, @PathVariable String movieId) {
        return ratingService.getOwnRating(principal.getName(), movieId);
    }

    @GetMapping("/api/movies/{movieId}/ratings")
    public List<MovieRatingView> getRatingsForMovie(@PathVariable String movieId) {
        return ratingService.getRatingsForMovie(movieId);
    }

    @GetMapping("/api/users/me/ratings")
    public List<UserRatingView> getMyRatings(Principal principal) {
        return ratingService.getRatingsByUser(principal.getName());
    }
}
