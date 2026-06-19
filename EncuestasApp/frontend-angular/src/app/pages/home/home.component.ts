import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { EncuestaService } from '../../core/api/encuesta.service';
import { EncuestaResumen } from '../../core/models/api.models';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>Encuestas activas</h1>
          <p class="page-subtitle">Participá en las votaciones disponibles</p>
        </div>
      </div>

      @if (loading) {
        <div class="spinner">Cargando encuestas...</div>
      } @else if (error) {
        <div class="alert alert-error">{{ error }}</div>
      } @else if (encuestas.length === 0) {
        <div class="empty">
          <div class="empty-icon">🗳️</div>
          <h3>Sin encuestas activas</h3>
          <p>No hay encuestas disponibles por el momento.</p>
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
                <span class="badge badge-activa">Activa</span>
              </div>

              <div class="survey-stats">
                <span>{{ e.totalOpciones }} opciones</span>
                <span>{{ e.totalVotos }} votos</span>
              </div>

              <div class="card-actions">
                @if (e.yaVoto) {
                  <span class="badge badge-finalizada" style="margin-right:8px">Ya votaste</span>
                  <a [routerLink]="['/encuesta', e.id]" class="btn btn-outline btn-sm">Ver resultados</a>
                } @else {
                  <a [routerLink]="['/encuesta', e.id]" class="btn btn-primary btn-sm">Votar ahora</a>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class HomeComponent implements OnInit {
  private encuestaService = inject(EncuestaService);

  encuestas: EncuestaResumen[] = [];
  loading = true;
  error   = '';

  ngOnInit(): void {
    this.encuestaService.listarActivas().subscribe({
      next:  (data) => { this.encuestas = data; this.loading = false; },
      error: ()     => { this.error = 'No se pudieron cargar las encuestas.'; this.loading = false; },
    });
  }
}
