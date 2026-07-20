import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RecommendationService } from '../../core/recommendation.service';
import { MovieRecommendation, RecommendationStrategy } from '../../core/models/recommendation.model';
import { copyMovieShareLink } from '../../core/share.util';

const STRATEGIES: { value: RecommendationStrategy; label: string }[] = [
  { value: 'content-based', label: 'Genres similaires' },
  { value: 'collaborative', label: 'Utilisateurs qui ont aimé les mêmes films' },
  { value: 'similar-users', label: 'Utilisateurs similaires (GDS)' },
];

@Component({
  selector: 'app-recommendations',
  imports: [FormsModule, RouterLink],
  templateUrl: './recommendations.html',
  styleUrl: './recommendations.scss',
})
export class Recommendations implements OnInit {
  readonly strategies = STRATEGIES;
  readonly recommendations = signal<MovieRecommendation[]>([]);
  readonly loading = signal(false);
  readonly sharedMovieId = signal<string | null>(null);

  strategy: RecommendationStrategy = 'content-based';
  genreFilter = '';
  fromFilter = '';
  toFilter = '';

  constructor(private recommendationService: RecommendationService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.recommendationService
      .get(this.strategy, { genre: this.genreFilter, from: this.fromFilter, to: this.toFilter })
      .subscribe({
        next: (recommendations) => {
          this.recommendations.set(recommendations);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  async share(movieId: string): Promise<void> {
    const ok = await copyMovieShareLink(movieId);
    if (ok) {
      this.sharedMovieId.set(movieId);
      setTimeout(() => this.sharedMovieId.set(null), 2000);
    }
  }
}
