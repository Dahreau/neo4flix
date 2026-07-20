package com.neo4flix.recommendation_service.service;

import com.neo4flix.recommendation_service.dto.MovieRecommendation;
import com.neo4flix.recommendation_service.repository.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;

    public RecommendationService(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    public List<MovieRecommendation> contentBased(String userId, String genre, LocalDate from, LocalDate to) {
        return recommendationRepository.contentBased(userId, genre, from, to);
    }

    public List<MovieRecommendation> collaborative(String userId, String genre, LocalDate from, LocalDate to) {
        return recommendationRepository.collaborative(userId, genre, from, to);
    }

    public List<MovieRecommendation> similarUsers(String userId, String genre, LocalDate from, LocalDate to) {
        return recommendationRepository.similarUsers(userId, genre, from, to);
    }
}
