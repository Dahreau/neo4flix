import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_CONFIG } from './api-config';
import { Movie } from './models/movie.model';

interface SearchParams {
  q?: string;
  genre?: string;
  from?: string;
  to?: string;
}

@Injectable({ providedIn: 'root' })
export class MovieService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<Movie[]>(`${API_CONFIG.movie}/movies`);
  }

  search(params: SearchParams) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value) {
        query.set(key, value);
      }
    });
    return this.http.get<Movie[]>(`${API_CONFIG.movie}/movies/search?${query.toString()}`);
  }

  get(movieId: string) {
    return this.http.get<Movie>(`${API_CONFIG.movie}/movies/${movieId}`);
  }

  genres() {
    return this.http.get<string[]>(`${API_CONFIG.movie}/genres`);
  }
}
