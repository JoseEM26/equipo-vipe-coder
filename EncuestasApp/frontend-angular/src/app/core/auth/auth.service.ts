import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegistroRequest } from '../models/api.models';

/**
 * Estado de sesión + autenticación.
 *
 * Persiste la sesión (token + datos del usuario) en localStorage y expone
 * señales reactivas (isAuthenticated, isAdmin) para usar en plantillas y guards.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;
  private readonly STORAGE_KEY = 'votaciones.session';

  private readonly _session = signal<AuthResponse | null>(this.loadSession());

  /** Sesión actual (solo lectura). */
  readonly session = this._session.asReadonly();

  readonly isAuthenticated = computed(() => {
    const s = this._session();
    return !!s && s.expiraEn > Date.now();
  });

  readonly isAdmin = computed(() => this._session()?.rol === 'admin');

  /** Token vigente, o null si no hay sesión o ya expiró (en cuyo caso limpia). */
  token(): string | null {
    const s = this._session();
    if (!s) return null;
    if (s.expiraEn <= Date.now()) {
      this.logout();
      return null;
    }
    return s.token;
  }

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/api/auth/login`, body)
      .pipe(tap((res) => this.setSession(res)));
  }

  register(body: RegistroRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/api/auth/registro`, body)
      .pipe(tap((res) => this.setSession(res)));
  }

  logout(): void {
    localStorage.removeItem(this.STORAGE_KEY);
    this._session.set(null);
  }

  // ── interno ─────────────────────────────────────────────────

  private setSession(res: AuthResponse): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(res));
    this._session.set(res);
  }

  private loadSession(): AuthResponse | null {
    const raw = localStorage.getItem(this.STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as AuthResponse;
    } catch {
      localStorage.removeItem(this.STORAGE_KEY);
      return null;
    }
  }
}
