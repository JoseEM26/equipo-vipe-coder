import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../core/models/api.models';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="auth-page">

      <!-- Panel izquierdo: branding -->
      <div class="auth-panel-left">
        <div class="brand-icon">🗳️</div>
        <h1>VotaYa</h1>
        <p>Creá tu cuenta y empezá a participar en votaciones en tiempo real</p>

        <div class="auth-features">
          <div class="auth-feature">
            <span class="auth-feature-icon">🚀</span>
            <span class="auth-feature-text">Acceso inmediato a encuestas activas</span>
          </div>
          <div class="auth-feature">
            <span class="auth-feature-icon">📈</span>
            <span class="auth-feature-text">Ve los resultados al instante</span>
          </div>
          <div class="auth-feature">
            <span class="auth-feature-icon">🎯</span>
            <span class="auth-feature-text">Tu voto es anónimo y seguro</span>
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
            <h2>Crear cuenta</h2>
            <p>Completá tus datos para registrarte</p>
          </div>

          @if (error) {
            <div class="alert alert-error">{{ error }}</div>
          }

          <form [formGroup]="form" (ngSubmit)="submit()">
            <div class="form-group">
              <label for="nombre">Nombre completo</label>
              <input
                id="nombre"
                type="text"
                formControlName="nombre"
                class="form-control"
                [class.is-invalid]="touched('nombre')"
                placeholder="Tu nombre"
                autocomplete="name"
              />
              @if (touched('nombre')) {
                <div class="form-error">El nombre es obligatorio</div>
              }
            </div>

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
                placeholder="Mínimo 8 caracteres"
                autocomplete="new-password"
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
              {{ loading ? 'Creando cuenta...' : 'Crear cuenta' }}
            </button>
          </form>

          <div class="auth-divider">o</div>

          <div class="auth-footer">
            ¿Ya tenés cuenta?&nbsp;<a routerLink="/login">Iniciar sesión</a>
          </div>
        </div>
      </div>

    </div>
  `,
})
export class RegistroComponent {
  private fb     = inject(FormBuilder);
  private auth   = inject(AuthService);
  private router = inject(Router);

  error   = '';
  loading = false;

  form = this.fb.group({
    nombre:   ['', [Validators.required, Validators.maxLength(100)]],
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

    this.auth.register(this.form.value as any).subscribe({
      next: () => { this.loading = false; this.router.navigate(['/']); },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error   = (err.error as ApiError)?.mensaje ?? 'Error al registrar. El email puede estar en uso.';
      },
    });
  }
}
