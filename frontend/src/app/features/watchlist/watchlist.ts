import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { WatchlistService } from '../../core/watchlist.service';
import { WatchlistItem } from '../../core/models/watchlist.model';

@Component({
  selector: 'app-watchlist',
  imports: [RouterLink],
  templateUrl: './watchlist.html',
  styleUrl: './watchlist.scss',
})
export class Watchlist implements OnInit {
  readonly items = signal<WatchlistItem[]>([]);
  readonly loading = signal(false);

  constructor(private watchlistService: WatchlistService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.watchlistService.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  remove(movieId: string): void {
    this.watchlistService.remove(movieId).subscribe({
      next: () => this.items.set(this.items().filter((item) => item.movieId !== movieId)),
    });
  }
}
