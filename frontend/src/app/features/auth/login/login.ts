import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  username = '';
  password = '';
  totpCode = '';

  readonly requiresTwoFactor = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.errorMessage.set(null);
    this.loading.set(true);

    this.auth.login({ username: this.username, password: this.password }).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (response.requiresTwoFactor) {
          this.requiresTwoFactor.set(true);
        } else if (response.auth) {
          this.auth.setSession(response.auth);
          this.router.navigateByUrl('/');
        }
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Identifiants invalides.');
      },
    });
  }

  submitTwoFactor(): void {
    this.errorMessage.set(null);
    this.loading.set(true);

    this.auth.verifyTwoFactor(this.username, this.totpCode).subscribe({
      next: (auth) => {
        this.loading.set(false);
        this.auth.setSession(auth);
        this.router.navigateByUrl('/');
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Code invalide ou expire.');
      },
    });
  }
}
