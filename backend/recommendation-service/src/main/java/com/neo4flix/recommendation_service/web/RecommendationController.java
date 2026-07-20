package com.neo4flix.recommendation_service.web;

import com.neo4flix.recommendation_service.dto.MovieRecommendation;
import com.neo4flix.recommendation_service.service.RecommendationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations/me")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/content-based")
    public List<MovieRecommendation> contentBased(Principal principal,
                                                    @RequestParam(required = false) String genre,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return recommendationService.contentBased(principal.getName(), genre, from, to);
    }

    @GetMapping("/collaborative")
    public List<MovieRecommendation> collaborative(Principal principal,
                                                     @RequestParam(required = false) String genre,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return recommendationService.collaborative(principal.getName(), genre, from, to);
    }

    @GetMapping("/similar-users")
    public List<MovieRecommendation> similarUsers(Principal principal,
                                                    @RequestParam(required = false) String genre,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return recommendationService.similarUsers(principal.getName(), genre, from, to);
    }
}
