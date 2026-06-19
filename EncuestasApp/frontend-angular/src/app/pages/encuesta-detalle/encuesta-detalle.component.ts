import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EncuestaService } from '../../core/api/encuesta.service';
import { VotoService } from '../../core/api/voto.service';
import { Encuesta, ResultadoEncuesta } from '../../core/models/api.models';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../../core/models/api.models';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-encuesta-detalle',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  template: `
    <div class="page" style="max-width:680px">
      <a routerLink="/" class="btn btn-ghost btn-sm" style="margin-bottom:16px">← Volver</a>

      @if (loading) {
        <div class="spinner">Cargando encuesta...</div>
      } @else if (error) {
        <div class="alert alert-error">{{ error }}</div>
      } @else if (encuesta) {

        <div class="card">
          <div class="card-header">
            <div>
              <h1 style="font-size:1.3rem;font-weight:700">{{ encuesta.titulo }}</h1>
              @if (encuesta.descripcion) {
                <p style="color:var(--text-muted);margin-top:6px">{{ encuesta.descripcion }}</p>
              }
            </div>
            <span class="badge badge-{{ encuesta.estado }}">{{ estadoLabel(encuesta.estado) }}</span>
          </div>

          <!-- ── Formulario de voto ─────────────────────────── -->
          @if (puedeVotar()) {
            <div style="margin-top:20px">
              <p style="font-size:.9rem;color:var(--text-muted);margin-bottom:14px">Seleccioná una opción:</p>

              @for (op of encuesta.opciones; track op.id) {
                <div class="opcion-voto" [class.selected]="opcionSeleccionada === op.id" (click)="opcionSeleccionada = op.id">
                  <input type="radio" [id]="op.id" name="opcion" [value]="op.id" [checked]="opcionSeleccionada === op.id" />
                  <label [for]="op.id">{{ op.texto }}</label>
                </div>
              }

              @if (errorVoto) {
                <div class="alert alert-error" style="margin-top:12px">{{ errorVoto }}</div>
              }

              <button
                class="btn btn-primary"
                style="margin-top:16px;width:100%;justify-content:center"
                [disabled]="!opcionSeleccionada || votando"
                (click)="votar()"
              >
                {{ votando ? 'Enviando voto...' : 'Confirmar voto' }}
              </button>
            </div>
          }

          <!-- ── Resultados ─────────────────────────────────── -->
          @if (resultado) {
            <div style="margin-top:20px">
              @if (votadoAhora) {
                <div class="alert alert-success" style="margin-bottom:16px">¡Tu voto fue registrado!</div>
              }
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
                <h2 style="font-size:1rem;font-weight:600">Resultados</h2>
                <span style="font-size:.88rem;color:var(--text-muted)">{{ resultado.totalVotos }} votos totales</span>
              </div>

              @for (op of resultado.opciones; track op.opcionId) {
                <div class="progress-wrap">
                  <div class="progress-label">
                    <span>{{ op.texto }}</span>
                    <strong>{{ op.porcentaje | number:'1.1-1' }}%</strong>
                  </div>
                  <div class="progress-bar">
                    <div class="progress-fill" [style.width.%]="op.porcentaje"></div>
                  </div>
                  <div class="progress-stats">{{ op.totalVotos }} votos</div>
                </div>
              }
            </div>
          }

          <!-- Estado sin resultados ─────────────────────────── -->
          @if (!puedeVotar() && !resultado && encuesta.estado === 'activa') {
            <div class="alert alert-info" style="margin-top:16px">Cargando resultados...</div>
          }
        </div>

      }
    </div>
  `,
})
export class EncuestaDetalleComponent implements OnInit {
  private route           = inject(ActivatedRoute);
  private encuestaService = inject(EncuestaService);
  private votoService     = inject(VotoService);

  encuesta: Encuesta | null = null;
  resultado: ResultadoEncuesta | null = null;
  opcionSeleccionada = '';
  loading    = true;
  votando    = false;
  votadoAhora = false;
  error      = '';
  errorVoto  = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.encuestaService.obtenerDetalle(id).subscribe({
      next: (enc) => {
        this.encuesta = enc;
        this.loading  = false;
        // cargar resultados si ya votó o está finalizada/cancelada
        if (enc.yaVoto || enc.estado === 'finalizada' || enc.estado === 'cancelada') {
          this.votoService.resultados(id).subscribe({
            next:  (r) => (this.resultado = r),
            error: ()  => {},
          });
        }
      },
      error: () => { this.error = 'No se pudo cargar la encuesta.'; this.loading = false; },
    });
  }

  puedeVotar(): boolean {
    return !!this.encuesta && this.encuesta.estado === 'activa' && !this.encuesta.yaVoto && !this.votadoAhora;
  }

  votar(): void {
    if (!this.opcionSeleccionada || !this.encuesta) return;
    this.votando   = true;
    this.errorVoto = '';

    this.votoService.votar(this.encuesta.id, this.opcionSeleccionada).subscribe({
      next: (res) => {
        this.resultado   = res;
        this.votadoAhora = true;
        this.votando     = false;
        if (this.encuesta) this.encuesta.yaVoto = true;
      },
      error: (err: HttpErrorResponse) => {
        this.votando   = false;
        this.errorVoto = (err.error as ApiError)?.mensaje ?? 'Error al registrar el voto.';
      },
    });
  }

  estadoLabel(estado: string): string {
    const map: Record<string, string> = {
      borrador: 'Borrador', activa: 'Activa', finalizada: 'Finalizada', cancelada: 'Cancelada',
    };
    return map[estado] ?? estado;
  }
}
