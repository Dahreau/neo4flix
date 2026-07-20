import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_CONFIG } from '../api-config';
import { AuthResponse, LoginRequest, LoginResponse, RegisterRequest } from '../models/auth.model';

const STORAGE_KEY = 'neo4flix_auth';

interface StoredAuth {
  token: string;
  userId: string;
  username: string;
}

// No SSR on this app (see ng new choice in docs), so touching localStorage
// directly in a field initializer is safe - always runs in the browser.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authState = signal<StoredAuth | null>(this.readFromStorage());

  readonly currentUser = computed(() => this.authState());
  readonly isAuthenticated = computed(() => this.authState() !== null);
  readonly token = computed(() => this.authState()?.token ?? null);

  constructor(private http: HttpClient) {}

  register(request: RegisterRequest) {
    return this.http.post<AuthResponse>(`${API_CONFIG.user}/auth/register`, request);
  }

  login(request: LoginRequest) {
    return this.http.post<LoginResponse>(`${API_CONFIG.user}/auth/login`, request);
  }

  verifyTwoFactor(username: string, code: string) {
    return this.http.post<AuthResponse>(`${API_CONFIG.user}/auth/2fa/verify-login`, { username, code });
  }

  setSession(auth: AuthResponse) {
    const stored: StoredAuth = { token: auth.token, userId: auth.userId, username: auth.username };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    this.authState.set(stored);
  }

  logout() {
    localStorage.removeItem(STORAGE_KEY);
    this.authState.set(null);
  }

  private readFromStorage(): StoredAuth | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as StoredAuth;
    } catch {
      return null;
    }
  }
}
