import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_CONFIG } from './api-config';
import { WatchlistItem } from './models/watchlist.model';

@Injectable({ providedIn: 'root' })
export class WatchlistService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get<WatchlistItem[]>(`${API_CONFIG.user}/users/me/watchlist`);
  }

  add(movieId: string) {
    return this.http.post<void>(`${API_CONFIG.user}/users/me/watchlist/${movieId}`, {});
  }

  remove(movieId: string) {
    return this.http.delete<void>(`${API_CONFIG.user}/users/me/watchlist/${movieId}`);
  }
}
