import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../core/models/api.models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">

      <!-- Panel izquierdo: branding -->
      <div class="auth-panel-left">
        <div class="brand-icon">🗳️</div>
        <h1>VotaYa</h1>
        <p>La plataforma de votaciones en tiempo real para tu equipo</p>

        <div class="auth-features">
          <div class="auth-feature">
            <span class="auth-feature-icon">⚡</span>
            <span class="auth-feature-text">Resultados en tiempo real</span>
          </div>
          <div class="auth-feature">
            <span class="auth-feature-icon">🔒</span>
            <span class="auth-feature-text">Un voto por usuario, garantizado</span>
          </div>
          <div class="auth-feature">
            <span class="auth-feature-icon">📊</span>
            <span class="auth-feature-text">Panel de administración completo</span>
          </div>
        </div>
      </div>

      <!-- Panel derecho: formulario -->
      <div class="auth-panel-right">
        <div class="auth-form-wrap">

          <div class="auth-logo">
            <div class="logo-badge">
              <span class="logo-dot"></span>
              <span class="logo-label">VotaYa</span>
            </div>
            <h2>Bienvenido de vuelta</h2>
            <p>Ingresá con tu cuenta para continuar</p>
          </div>

          @if (error) {
            <div class="alert alert-error">{{ error }}</div>
          }

          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="form-group">
              <label for="email">Correo electrónico</label>
              <input
                id="email"
                type="email"
                formControlName="email"
                class="form-control"
                [class.is-invalid]="touched('email')"
                placeholder="tu@email.com"
                autocomplete="email"
              />
              @if (touched('email')) {
                <div class="form-error">Ingresá un email válido</div>
              }
            </div>

            <div class="form-group">
              <label for="password">Contraseña</label>
              <input
                id="password"
                type="password"
                formControlName="password"
                class="form-control"
                [class.is-invalid]="touched('password')"
                placeholder="••••••••"
                autocomplete="current-password"
              />
              @if (touched('password')) {
                <div class="form-error">Mínimo 8 caracteres</div>
              }
            </div>

            <button
              type="submit"
              class="btn btn-primary btn-lg"
              style="width:100%;margin-top:4px"
              [disabled]="loading"
            >
              {{ loading ? 'Ingresando...' : 'Iniciar sesión' }}
            </button>
          </form>

          <div class="auth-divider">o</div>

          <div class="auth-footer">
            ¿No tenés cuenta?&nbsp;<a routerLink="/registro">Crear cuenta gratis</a>
          </div>
        </div>
      </div>

    </div>
  `,
})
export class LoginComponent {
  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  error   = '';
  loading = false;

  form = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  touched(field: string): boolean {
    const c = this.form.get(field);
    return !!(c && c.invalid && c.touched);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading = true;
    this.error   = '';

    this.auth.login(this.form.value as any).subscribe({
      next: (res) => {
        this.loading = false;
        this.router.navigate([res.rol === 'admin' ? '/admin' : '/']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error   = (err.error as ApiError)?.mensaje ?? 'Credenciales incorrectas';
      },
    });
  }
}
