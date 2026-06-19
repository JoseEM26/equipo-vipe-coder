import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResultadoEncuesta, VotarRequest } from '../models/api.models';

/** Votación y consulta de resultados (REST). */
@Injectable({ providedIn: 'root' })
export class VotoService {

  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/api/encuestas`;

  /**
   * Emite un voto. La respuesta ya trae los resultados actualizados, úsalos
   * para mostrar el desglose al usuario sin pedir /resultados aparte.
   */
  votar(encuestaId: string, opcionId: string): Observable<ResultadoEncuesta> {
    const body: VotarRequest = { opcionId };
    return this.http.post<ResultadoEncuesta>(`${this.url}/${encuestaId}/votar`, body);
  }

  /**
   * Resultados agregados. Visibilidad:
   *  - admin: siempre,
   *  - usuario: solo si ya votó o si la encuesta finalizó (si no, 403).
   */
  resultados(encuestaId: string): Observable<ResultadoEncuesta> {
    return this.http.get<ResultadoEncuesta>(`${this.url}/${encuestaId}/resultados`);
  }
}
