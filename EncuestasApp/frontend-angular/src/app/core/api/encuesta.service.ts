import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CambiarEstadoRequest,
  Encuesta,
  EncuestaRequest,
  EncuestaResumen,
  EstadoEncuesta,
} from '../models/api.models';

/** Operaciones sobre encuestas (REST). */
@Injectable({ providedIn: 'root' })
export class EncuestaService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/encuestas`;

  // ── Admin ───────────────────────────────────────────────────

  /** Panel admin: todas las encuestas. */
  listarTodas(): Observable<EncuestaResumen[]> {
    return this.http.get<EncuestaResumen[]>(this.url);
  }

  crear(body: EncuestaRequest): Observable<Encuesta> {
    return this.http.post<Encuesta>(this.url, body);
  }

  actualizar(id: string, body: EncuestaRequest): Observable<Encuesta> {
    return this.http.put<Encuesta>(`${this.url}/${id}`, body);
  }

  cambiarEstado(id: string, estado: Exclude<EstadoEncuesta, 'borrador'>): Observable<Encuesta> {
    const body: CambiarEstadoRequest = { estado };
    return this.http.patch<Encuesta>(`${this.url}/${id}/estado`, body);
  }

  eliminar(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  // ── Autenticados (admin o usuario) ──────────────────────────

  /** Encuestas activas disponibles (con marca "ya votó"). */
  listarActivas(): Observable<EncuestaResumen[]> {
    return this.http.get<EncuestaResumen[]>(`${this.url}/activas`);
  }

  /** Finalizadas: admin todas, usuario solo en las que participó. */
  listarFinalizadas(): Observable<EncuestaResumen[]> {
    return this.http.get<EncuestaResumen[]>(`${this.url}/finalizadas`);
  }

  obtenerDetalle(id: string): Observable<Encuesta> {
    return this.http.get<Encuesta>(`${this.url}/${id}`);
  }
}
