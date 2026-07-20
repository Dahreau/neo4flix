import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_CONFIG } from './api-config';
import { MovieRecommendation, RecommendationStrategy } from './models/recommendation.model';

interface RecommendationFilters {
  genre?: string;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  constructor(private http: HttpClient) {}

  get(strategy: RecommendationStrategy, filters: RecommendationFilters = {}) {
    const query = new URLSearchParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value) {
        query.set(key, value);
      }
    });
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return this.http.get<MovieRecommendation[]>(
      `${API_CONFIG.recommendation}/recommendations/me/${strategy}${suffix}`
    );
  }
}
