import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_CONFIG } from './api-config';
import { MovieRatingView, OwnRatingView, RatingRequest, UserRatingView } from './models/rating.model';

@Injectable({ providedIn: 'root' })
export class RatingService {
  constructor(private http: HttpClient) {}

  rate(movieId: string, request: RatingRequest) {
    return this.http.put<void>(`${API_CONFIG.rating}/movies/${movieId}/rating`, request);
  }

  unrate(movieId: string) {
    return this.http.delete<void>(`${API_CONFIG.rating}/movies/${movieId}/rating`);
  }

  getOwnRating(movieId: string) {
    return this.http.get<OwnRatingView>(`${API_CONFIG.rating}/movies/${movieId}/rating`);
  }

  getRatingsForMovie(movieId: string) {
    return this.http.get<MovieRatingView[]>(`${API_CONFIG.rating}/movies/${movieId}/ratings`);
  }

  getMyRatings() {
    return this.http.get<UserRatingView[]>(`${API_CONFIG.rating}/users/me/ratings`);
  }
}
