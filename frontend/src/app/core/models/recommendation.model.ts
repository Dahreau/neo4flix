// relevanceScore's meaning depends on the strategy: shared genre count (content-based),
// predicted average score from similar users (collaborative / similar-users).
export interface MovieRecommendation {
  movieId: string;
  title: string;
  posterUrl: string | null;
  averageRating: number | null;
  relevanceScore: number;
}

export type RecommendationStrategy = 'content-based' | 'collaborative' | 'similar-users';
