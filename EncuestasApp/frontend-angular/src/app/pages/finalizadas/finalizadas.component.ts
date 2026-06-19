import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EncuestaService } from '../../core/api/encuesta.service';
import { EncuestaResumen } from '../../core/models/api.models';

@Component({
  selector: 'app-finalizadas',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>Encuestas finalizadas</h1>
          <p class="page-subtitle">Votaciones en las que participaste</p>
        </div>
      </div>

      @if (loading) {
        <div class="spinner">Cargando...</div>
      } @else if (error) {
        <div class="alert alert-error">{{ error }}</div>
      } @else if (encuestas.length === 0) {
        <div class="empty">
          <div class="empty-icon">📋</div>
          <h3>Sin encuestas finalizadas</h3>
          <p>Todavía no participaste en ninguna encuesta finalizada.</p>
        </div>
      } @else {
        <div class="surveys-grid">
          @for (e of encuestas; track e.id) {
            <div class="card">
              <div class="card-header">
                <div>
                  <div class="card-title">{{ e.titulo }}</div>
                  @if (e.descripcion) {
                    <p class="survey-desc">{{ e.descripcion }}</p>
                  }
                </div>
                <span class="badge badge-{{ e.estado }}">{{ estadoLabel(e.estado) }}</span>
              </div>

              <div class="survey-stats">
                <span>{{ e.totalVotos }} votos totales</span>
                <span>{{ e.totalOpciones }} opciones</span>
              </div>

              <div class="card-actions">
                <a [routerLink]="['/encuesta', e.id]" class="btn btn-outline btn-sm">Ver resultados</a>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class FinalizadasComponent implements OnInit {
  private encuestaService = inject(EncuestaService);

  encuestas: EncuestaResumen[] = [];
  loading = true;
  error   = '';

  ngOnInit(): void {
    this.encuestaService.listarFinalizadas().subscribe({
      next:  (data) => { this.encuestas = data; this.loading = false; },
      error: ()     => { this.error = 'No se pudieron cargar las encuestas.'; this.loading = false; },
    });
  }

  estadoLabel(estado: string): string {
    const map: Record<string, string> = { finalizada: 'Finalizada', cancelada: 'Cancelada' };
    return map[estado] ?? estado;
  }
}
