import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MovieService } from '../../core/movie.service';
import { RatingService } from '../../core/rating.service';
import { WatchlistService } from '../../core/watchlist.service';
import { AuthService } from '../../core/auth/auth.service';
import { Movie } from '../../core/models/movie.model';
import { MovieRatingView, OwnRatingView } from '../../core/models/rating.model';
import { copyMovieShareLink } from '../../core/share.util';

@Component({
  selector: 'app-movie-detail',
  imports: [FormsModule, RouterLink],
  templateUrl: './movie-detail.html',
  styleUrl: './movie-detail.scss',
})
export class MovieDetail implements OnInit {
  readonly movie = signal<Movie | null>(null);
  readonly ratings = signal<MovieRatingView[]>([]);
  readonly ownRating = signal<OwnRatingView | null>(null);
  readonly inWatchlist = signal(false);
  readonly loading = signal(false);
  readonly shareConfirmed = signal(false);

  scoreInput = 4;
  commentInput = '';

  private movieId = '';

  constructor(
    private route: ActivatedRoute,
    private movieService: MovieService,
    private ratingService: RatingService,
    private watchlistService: WatchlistService,
    readonly auth: AuthService
  ) {}

  ngOnInit(): void {
    this.movieId = this.route.snapshot.paramMap.get('id') ?? '';
    this.loadMovie();
    this.loadRatings();
    if (this.auth.isAuthenticated()) {
      this.loadOwnRating();
      this.loadWatchlistStatus();
    }
  }

  private loadMovie(): void {
    this.loading.set(true);
    this.movieService.get(this.movieId).subscribe({
      next: (movie) => {
        this.movie.set(movie);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadRatings(): void {
    this.ratingService.getRatingsForMovie(this.movieId).subscribe({
      next: (ratings) => this.ratings.set(ratings),
      error: () => this.ratings.set([]),
    });
  }

  private loadOwnRating(): void {
    // 404 just means "not rated yet" here - not an error worth surfacing to the user.
    this.ratingService.getOwnRating(this.movieId).subscribe({
      next: (rating) => {
        this.ownRating.set(rating);
        this.scoreInput = rating.score;
        this.commentInput = rating.comment ?? '';
      },
      error: () => this.ownRating.set(null),
    });
  }

  private loadWatchlistStatus(): void {
    this.watchlistService.list().subscribe({
      next: (items) => this.inWatchlist.set(items.some((item) => item.movieId === this.movieId)),
      error: () => this.inWatchlist.set(false),
    });
  }

  submitRating(): void {
    this.ratingService.rate(this.movieId, { score: this.scoreInput, comment: this.commentInput }).subscribe({
      next: () => {
        this.loadMovie();
        this.loadRatings();
        this.loadOwnRating();
      },
    });
  }

  deleteRating(): void {
    this.ratingService.unrate(this.movieId).subscribe({
      next: () => {
        this.ownRating.set(null);
        this.scoreInput = 4;
        this.commentInput = '';
        this.loadMovie();
        this.loadRatings();
      },
    });
  }

  toggleWatchlist(): void {
    const action = this.inWatchlist()
      ? this.watchlistService.remove(this.movieId)
      : this.watchlistService.add(this.movieId);

    action.subscribe({
      next: () => this.inWatchlist.set(!this.inWatchlist()),
    });
  }

  async share(): Promise<void> {
    const ok = await copyMovieShareLink(this.movieId);
    this.shareConfirmed.set(ok);
    if (ok) {
      setTimeout(() => this.shareConfirmed.set(false), 2000);
    }
  }
}
