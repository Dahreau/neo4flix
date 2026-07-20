import { Routes } from '@angular/router';
import { Home } from './features/home/home';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { MovieDetail } from './features/movie-detail/movie-detail';
import { Watchlist } from './features/watchlist/watchlist';
import { Recommendations } from './features/recommendations/recommendations';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'movies/:id', component: MovieDetail },
  { path: 'watchlist', component: Watchlist, canActivate: [authGuard] },
  { path: 'recommendations', component: Recommendations, canActivate: [authGuard] },
];
