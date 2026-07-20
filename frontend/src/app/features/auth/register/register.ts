import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  username = '';
  email = '';
  password = '';

  readonly errorMessage = signal<string | null>(null);
  readonly loading = signal(false);

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.errorMessage.set(null);
    this.loading.set(true);

    this.auth
      .register({ username: this.username, email: this.email, password: this.password })
      .subscribe({
        next: (auth) => {
          this.loading.set(false);
          this.auth.setSession(auth);
          this.router.navigateByUrl('/');
        },
        error: (err) => {
          this.loading.set(false);
          this.errorMessage.set(err?.error?.message ?? "Inscription impossible.");
        },
      });
  }
}
