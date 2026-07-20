import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MovieService } from '../../core/movie.service';
import { Movie } from '../../core/models/movie.model';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home',
  imports: [FormsModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  readonly movies = signal<Movie[]>([]);
  readonly loading = signal(false);
  searchQuery = '';

  constructor(private movieService: MovieService, readonly auth: AuthService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loading.set(true);
    this.movieService.list().subscribe({
      next: (movies) => {
        this.movies.set(movies);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  search(): void {
    if (!this.searchQuery.trim()) {
      this.loadAll();
      return;
    }

    this.loading.set(true);
    this.movieService.search({ q: this.searchQuery }).subscribe({
      next: (movies) => {
        this.movies.set(movies);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
