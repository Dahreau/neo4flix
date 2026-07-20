package com.neo4flix.rating_service.service;

import com.neo4flix.rating_service.dto.MovieRatingView;
import com.neo4flix.rating_service.dto.OwnRatingView;
import com.neo4flix.rating_service.dto.RatingRequest;
import com.neo4flix.rating_service.dto.UserRatingView;
import com.neo4flix.rating_service.exception.MovieNotFoundException;
import com.neo4flix.rating_service.exception.RatingNotFoundException;
import com.neo4flix.rating_service.exception.UserNotFoundException;
import com.neo4flix.rating_service.repository.RatingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;

    public RatingService(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public void rate(String userId, String movieId, RatingRequest request) {
        if (request.score() == null || request.score() < 0.5 || request.score() > 5.0) {
            throw new IllegalArgumentException("La note doit etre comprise entre 0.5 et 5.0");
        }
        if (!ratingRepository.userExists(userId)) {
            throw new UserNotFoundException(userId);
        }
        if (!ratingRepository.movieExists(movieId)) {
            throw new MovieNotFoundException(movieId);
        }

        ratingRepository.upsertRating(userId, movieId, request.score(), request.comment());
    }

    public void unrate(String userId, String movieId) {
        ratingRepository.deleteRating(userId, movieId);
    }

    public OwnRatingView getOwnRating(String userId, String movieId) {
        return ratingRepository.findOwnRating(userId, movieId)
                .orElseThrow(() -> new RatingNotFoundException(userId, movieId));
    }

    public List<MovieRatingView> getRatingsForMovie(String movieId) {
        if (!ratingRepository.movieExists(movieId)) {
            throw new MovieNotFoundException(movieId);
        }
        return ratingRepository.findRatingsForMovie(movieId);
    }

    public List<UserRatingView> getRatingsByUser(String userId) {
        return ratingRepository.findRatingsByUser(userId);
    }
}
