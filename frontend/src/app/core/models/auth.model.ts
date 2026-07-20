export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
}

// auth is null when requiresTwoFactor is true - see backend docs/04-security.md.
export interface LoginResponse {
  requiresTwoFactor: boolean;
  auth: AuthResponse | null;
}
