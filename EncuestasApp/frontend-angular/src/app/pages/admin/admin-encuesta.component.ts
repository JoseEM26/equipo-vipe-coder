import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { EncuestaService } from '../../core/api/encuesta.service';
import { VotoService } from '../../core/api/voto.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { Encuesta, EstadoEncuesta, ResultadoEncuesta } from '../../core/models/api.models';

@Component({
  selector: 'app-admin-encuesta',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <div class="page" style="max-width:760px">
      <a routerLink="/admin" class="btn btn-ghost btn-sm" style="margin-bottom:16px">← Volver al panel</a>

      @if (loading) {
        <div class="spinner">Cargando...</div>
      } @else if (error) {
        <div class="alert alert-error">{{ error }}</div>
      } @else if (encuesta) {

        <!-- encabezado -->
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div>
              <h1 style="font-size:1.25rem;font-weight:700">{{ encuesta.titulo }}</h1>
              @if (encuesta.descripcion) {
                <p style="color:var(--text-muted);margin-top:4px;font-size:.9rem">{{ encuesta.descripcion }}</p>
              }
            </div>
            <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px">
              <span class="badge badge-{{ encuesta.estado }}">{{ estadoLabel(encuesta.estado) }}</span>
              @if (encuesta.estado === 'activa') {
                <span class="badge badge-live">● EN VIVO</span>
              }
            </div>
          </div>

          @if (encuesta.estado === 'activa') {
            <div class="card-actions">
              <button class="btn btn-outline btn-sm" (click)="cambiarEstado('finalizada')" [disabled]="operando">Finalizar encuesta</button>
              <button class="btn btn-danger  btn-sm" (click)="cambiarEstado('cancelada')"  [disabled]="operando">Cancelar encuesta</button>
            </div>
          }

          @if (wsError) {
            <div class="alert alert-error" style="margin-top:12px">{{ wsError }}</div>
          }
        </div>

        <!-- resultados -->
        <div class="card">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
            <h2 style="font-size:1rem;font-weight:600">Resultados</h2>
            @if (resultado) {
              <span style="font-size:.88rem;color:var(--text-muted)">{{ resultado.totalVotos }} votos totales</span>
            }
          </div>

          @if (!resultado) {
            <div class="empty" style="padding:32px">
              <div class="empty-icon">🗳️</div>
              <p>Aún no hay votos registrados.</p>
            </div>
          } @else {
            @for (op of resultado.opciones; track op.opcionId) {
              <div class="progress-wrap">
                <div class="progress-label">
                  <span style="font-weight:500">{{ op.texto }}</span>
                  <strong>{{ op.porcentaje | number:'1.1-1' }}%</strong>
                </div>
                <div class="progress-bar">
                  <div class="progress-fill" [style.width.%]="op.porcentaje"></div>
                </div>
                <div class="progress-stats">{{ op.totalVotos }} votos</div>
              </div>
            }
          }
        </div>

      }
    </div>
  `,
})
export class AdminEncuestaComponent implements OnInit, OnDestroy {
  private route           = inject(ActivatedRoute);
  private router          = inject(Router);
  private encuestaService = inject(EncuestaService);
  private votoService     = inject(VotoService);
  private realtime        = inject(RealtimeService);

  encuesta: Encuesta | null            = null;
  resultado: ResultadoEncuesta | null  = null;
  loading  = true;
  error    = '';
  wsError  = '';
  operando = false;

  private stopWs: (() => void) | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;

    this.encuestaService.obtenerDetalle(id).subscribe({
      next: (enc) => {
        this.encuesta = enc;
        this.loading  = false;
        this.cargarResultados(id);

        if (enc.estado === 'activa') {
          this.stopWs = this.realtime.watchResultados(
            id,
            (r) => (this.resultado = r),
            ()  => (this.wsError = 'Error en la conexión en tiempo real. Actualizando solo al recargar.'),
          );
        }
      },
      error: () => { this.error = 'No se pudo cargar la encuesta.'; this.loading = false; },
    });
  }

  ngOnDestroy(): void { this.stopWs?.(); }

  private cargarResultados(id: string): void {
    this.votoService.resultados(id).subscribe({
      next:  (r) => (this.resultado = r),
      error: ()  => {},
    });
  }

  cambiarEstado(estado: Exclude<EstadoEncuesta, 'borrador'>): void {
    if (!this.encuesta) return;
    const label = estado === 'finalizada' ? 'finalizar' : 'cancelar';
    if (!confirm(`¿Seguro que querés ${label} esta encuesta?`)) return;

    this.operando = true;
    this.encuestaService.cambiarEstado(this.encuesta.id, estado).subscribe({
      next: (enc) => {
        this.operando = false;
        this.encuesta = enc;
        this.stopWs?.();
        this.stopWs = null;
        this.cargarResultados(enc.id);
      },
      error: () => { this.operando = false; },
    });
  }

  estadoLabel(estado: string): string {
    const map: Record<string, string> = {
      borrador: 'Borrador', activa: 'Activa', finalizada: 'Finalizada', cancelada: 'Cancelada',
    };
    return map[estado] ?? estado;
  }
}
