import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { EncuestaService } from '../../core/api/encuesta.service';
import { ApiError, EncuestaRequest, EncuestaResumen, EstadoEncuesta } from '../../core/models/api.models';

@Component({
  selector: 'app-admin-panel',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, SlicePipe],
  template: `
    <div class="page">

      <!-- encabezado -->
      <div class="page-header">
        <div>
          <h1>Panel de administración</h1>
          <p class="page-subtitle">Gestioná todas las encuestas del sistema</p>
        </div>
        <button class="btn btn-primary" (click)="abrirFormulario()">+ Nueva encuesta</button>
      </div>

      <!-- ── Formulario crear / editar ──────────────────────── -->
      @if (mostrarForm) {
        <div class="form-section">
          <h2>{{ editandoId ? 'Editar encuesta' : 'Nueva encuesta' }}</h2>

          @if (errorForm) { <div class="alert alert-error">{{ errorForm }}</div> }

          <form [formGroup]="form" (ngSubmit)="guardar()">
            <div class="form-group">
              <label>Título *</label>
              <input formControlName="titulo" class="form-control" [class.is-invalid]="touchedForm('titulo')" placeholder="Título de la encuesta" />
              @if (touchedForm('titulo')) { <div class="form-error">El título es obligatorio</div> }
            </div>

            <div class="form-group">
              <label>Descripción <span style="color:var(--text-muted)">(opcional)</span></label>
              <textarea formControlName="descripcion" class="form-control" rows="2" placeholder="Descripción breve..."></textarea>
            </div>

            <div class="form-group">
              <label>Opciones * <span style="color:var(--text-muted)">(mínimo 2)</span></label>
              @for (op of opciones; track $index) {
                <div class="opcion-row">
                  <input
                    class="form-control"
                    [value]="op"
                    (input)="setOpcion($index, $any($event).target.value)"
                    [placeholder]="'Opción ' + ($index + 1)"
                  />
                  @if (opciones.length > 2) {
                    <button type="button" class="btn-remove" (click)="removeOpcion($index)" title="Eliminar opción">×</button>
                  }
                </div>
              }
              <button type="button" class="btn btn-outline btn-sm" style="margin-top:4px" (click)="addOpcion()">+ Agregar opción</button>
            </div>

            <div style="display:flex;gap:10px;margin-top:8px">
              <button type="submit" class="btn btn-primary" [disabled]="guardando">
                {{ guardando ? 'Guardando...' : (editandoId ? 'Guardar cambios' : 'Crear encuesta') }}
              </button>
              <button type="button" class="btn btn-outline" (click)="cerrarFormulario()">Cancelar</button>
            </div>
          </form>
        </div>
      }

      <!-- ── Lista de encuestas ─────────────────────────────── -->
      @if (loading) {
        <div class="spinner">Cargando encuestas...</div>
      } @else if (error) {
        <div class="alert alert-error">{{ error }}</div>
      } @else if (encuestas.length === 0) {
        <div class="empty">
          <div class="empty-icon">📋</div>
          <h3>Sin encuestas</h3>
          <p>Creá la primera encuesta con el botón de arriba.</p>
        </div>
      } @else {
        <div class="card" style="padding:0;overflow:hidden">
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Encuesta</th>
                  <th>Estado</th>
                  <th style="text-align:center">Votos</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                @for (e of encuestas; track e.id) {
                  <tr>
                    <td>
                      <div style="font-weight:500">{{ e.titulo }}</div>
                      @if (e.descripcion) {
                        <div style="font-size:.82rem;color:var(--text-muted);margin-top:2px">{{ e.descripcion | slice:0:60 }}{{ (e.descripcion || '').length > 60 ? '…' : '' }}</div>
                      }
                    </td>
                    <td><span class="badge badge-{{ e.estado }}">{{ estadoLabel(e.estado) }}</span></td>
                    <td style="text-align:center">{{ e.totalVotos }}</td>
                    <td>
                      <div class="td-actions">
                        @if (e.estado === 'borrador') {
                          <button class="btn btn-outline btn-sm" (click)="editarEncuesta(e)" [disabled]="operando === e.id">Editar</button>
                          <button class="btn btn-primary btn-sm" (click)="cambiarEstado(e.id, 'activa')" [disabled]="operando === e.id">Activar</button>
                          <button class="btn btn-danger  btn-sm" (click)="eliminar(e.id)" [disabled]="operando === e.id">Eliminar</button>
                        }
                        @if (e.estado === 'activa') {
                          <a [routerLink]="['/admin/encuesta', e.id]" class="btn btn-primary btn-sm">Ver en vivo</a>
                          <button class="btn btn-outline btn-sm" (click)="cambiarEstado(e.id, 'finalizada')" [disabled]="operando === e.id">Finalizar</button>
                          <button class="btn btn-danger  btn-sm" (click)="cambiarEstado(e.id, 'cancelada')" [disabled]="operando === e.id">Cancelar</button>
                        }
                        @if (e.estado === 'finalizada' || e.estado === 'cancelada') {
                          <a [routerLink]="['/admin/encuesta', e.id]" class="btn btn-outline btn-sm">Ver resultados</a>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

    </div>
  `,
})
export class AdminPanelComponent implements OnInit {
  private encuestaService = inject(EncuestaService);
  private fb              = inject(FormBuilder);

  encuestas: EncuestaResumen[] = [];
  loading    = true;
  error      = '';
  mostrarForm = false;
  editandoId: string | null = null;
  guardando  = false;
  errorForm  = '';
  operando   = '';  // id del item en proceso de cambio de estado
  opciones   = ['', ''];

  form = this.fb.group({
    titulo:      ['', [Validators.required, Validators.maxLength(255)]],
    descripcion: ['', Validators.maxLength(2000)],
  });

  ngOnInit(): void { this.cargar(); }

  cargar(): void {
    this.loading = true;
    this.encuestaService.listarTodas().subscribe({
      next:  (data) => { this.encuestas = data; this.loading = false; },
      error: ()     => { this.error = 'Error al cargar las encuestas.'; this.loading = false; },
    });
  }

  // ── Formulario ───────────────────────────────────────────────

  abrirFormulario(): void {
    this.editandoId = null;
    this.form.reset();
    this.opciones   = ['', ''];
    this.errorForm  = '';
    this.mostrarForm = true;
  }

  editarEncuesta(e: EncuestaResumen): void {
    this.editandoId = e.id;
    this.form.patchValue({ titulo: e.titulo, descripcion: e.descripcion ?? '' });
    // Cargar opciones del detalle
    this.encuestaService.obtenerDetalle(e.id).subscribe({
      next: (det) => { this.opciones = det.opciones.map(o => o.texto); },
      error: ()   => { this.opciones = ['', '']; },
    });
    this.errorForm   = '';
    this.mostrarForm = true;
  }

  cerrarFormulario(): void { this.mostrarForm = false; this.editandoId = null; }

  setOpcion(i: number, val: string): void { this.opciones[i] = val; }
  addOpcion():           void { this.opciones.push(''); }
  removeOpcion(i: number): void { this.opciones.splice(i, 1); }

  guardar(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const opcionesLimpias = this.opciones.map(o => o.trim()).filter(o => o.length > 0);
    if (opcionesLimpias.length < 2) { this.errorForm = 'Se necesitan al menos 2 opciones.'; return; }

    this.guardando = true;
    this.errorForm = '';
    const body: EncuestaRequest = {
      titulo:      this.form.value.titulo!,
      descripcion: this.form.value.descripcion || undefined,
      opciones:    opcionesLimpias,
    };

    const req = this.editandoId
      ? this.encuestaService.actualizar(this.editandoId, body)
      : this.encuestaService.crear(body);

    req.subscribe({
      next: () => { this.guardando = false; this.cerrarFormulario(); this.cargar(); },
      error: (err: HttpErrorResponse) => {
        this.guardando = false;
        this.errorForm = (err.error as ApiError)?.mensaje ?? 'Error al guardar la encuesta.';
      },
    });
  }

  // ── Acciones sobre estado ────────────────────────────────────

  cambiarEstado(id: string, estado: Exclude<EstadoEncuesta, 'borrador'>): void {
    this.operando = id;
    this.encuestaService.cambiarEstado(id, estado).subscribe({
      next:  () => { this.operando = ''; this.cargar(); },
      error: () => { this.operando = ''; },
    });
  }

  eliminar(id: string): void {
    if (!confirm('¿Eliminar esta encuesta? Esta acción no se puede deshacer.')) return;
    this.operando = id;
    this.encuestaService.eliminar(id).subscribe({
      next:  () => { this.operando = ''; this.cargar(); },
      error: () => { this.operando = ''; },
    });
  }

  // ── Helpers ─────────────────────────────────────────────────

  touchedForm(field: string): boolean {
    const c = this.form.get(field);
    return !!(c && c.invalid && c.touched);
  }

  estadoLabel(estado: string): string {
    const map: Record<string, string> = {
      borrador: 'Borrador', activa: 'Activa', finalizada: 'Finalizada', cancelada: 'Cancelada',
    };
    return map[estado] ?? estado;
  }
}
