export interface Movie {
  movieId: string;
  title: string;
  originalTitle: string | null;
  releaseDate: string | null;
  durationMinutes: number | null;
  synopsis: string | null;
  posterUrl: string | null;
  averageRating: number | null;
  genres: string[];
}

export interface MovieRequest {
  title: string;
  originalTitle?: string;
  releaseDate?: string;
  durationMinutes?: number;
  synopsis?: string;
  posterUrl?: string;
  genres?: string[];
}
