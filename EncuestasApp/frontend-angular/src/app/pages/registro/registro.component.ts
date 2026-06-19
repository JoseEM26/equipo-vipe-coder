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
      <div class="auth-card card">
        <div class="auth-logo">
          <h1>VotaYa</h1>
          <p>Crear cuenta nueva</p>
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

          <button type="submit" class="btn btn-primary" style="width:100%;justify-content:center" [disabled]="loading">
            {{ loading ? 'Creando cuenta...' : 'Crear cuenta' }}
          </button>
        </form>

        <div class="auth-footer">
          ¿Ya tenés cuenta? <a routerLink="/login">Iniciar sesión</a>
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
