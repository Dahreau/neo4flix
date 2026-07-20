export interface RatingRequest {
  score: number;
  comment?: string;
}

export interface OwnRatingView {
  score: number;
  comment: string | null;
  ratedAt: string;
}

// Rating as seen from a movie page - who rated it.
export interface MovieRatingView {
  userId: string;
  username: string;
  score: number;
  comment: string | null;
  ratedAt: string;
}

// Rating as seen from a user profile - which movie.
export interface UserRatingView {
  movieId: string;
  movieTitle: string;
  score: number;
  comment: string | null;
  ratedAt: string;
}
