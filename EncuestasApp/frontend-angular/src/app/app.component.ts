import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.isAuthenticated()) {
      <nav class="navbar">
        <div class="navbar-inner">

          <div class="nav-brand">
            <a routerLink="/">🗳️ VotaYa</a>
          </div>

          <div class="nav-links">
            @if (auth.isAdmin()) {
              <a routerLink="/admin" routerLinkActive="active">Panel Admin</a>
            } @else {
              <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}">Inicio</a>
              <a routerLink="/finalizadas" routerLinkActive="active">Finalizadas</a>
            }
          </div>

          <div class="nav-user">
            <span class="nav-name">{{ auth.session()?.nombre }}</span>
            <div class="nav-avatar" [title]="auth.session()?.email ?? ''">
              {{ inicial() }}
            </div>
            <button class="btn btn-outline btn-sm" (click)="logout()">Salir</button>
          </div>

        </div>
      </nav>
    }

    <router-outlet />
  `,
})
export class AppComponent {
  protected auth = inject(AuthService);
  private router = inject(Router);

  inicial(): string {
    return (this.auth.session()?.nombre ?? '?').charAt(0).toUpperCase();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
