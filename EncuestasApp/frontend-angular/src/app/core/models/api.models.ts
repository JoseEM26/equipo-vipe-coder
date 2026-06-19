/**
 * Tipos TypeScript que reflejan los DTOs del backend (Votaciones API).
 * Mantener sincronizados con los records de Java.
 */

export type Rol = 'admin' | 'usuario';

export type EstadoEncuesta = 'borrador' | 'activa' | 'finalizada' | 'cancelada';

// ── Auth ──────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegistroRequest {
  nombre: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  usuarioId: string;
  nombre: string;
  email: string;
  rol: Rol;
  /** Epoch en ms en que expira el token. */
  expiraEn: number;
}

// ── Encuestas ─────────────────────────────────────────────────

export interface EncuestaRequest {
  titulo: string;
  descripcion?: string;
  /** Mínimo 2 opciones. */
  opciones: string[];
}

export interface Opcion {
  id: string;
  texto: string;
  orden: number;
}

/** Detalle de una encuesta (GET /api/encuestas/{id}). */
export interface Encuesta {
  id: string;
  titulo: string;
  descripcion?: string;
  estado: EstadoEncuesta;
  creadoEn: string;
  activadaEn?: string;
  finalizadaEn?: string;
  canceladaEn?: string;
  opciones: Opcion[];
  /** null/ausente cuando lo consulta un admin. */
  yaVoto?: boolean;
}

/** Resumen para listados. */
export interface EncuestaResumen {
  id: string;
  titulo: string;
  descripcion?: string;
  estado: EstadoEncuesta;
  creadoEn: string;
  totalOpciones: number;
  totalVotos: number;
  /** null/ausente en vistas de admin. */
  yaVoto?: boolean;
}

export interface CambiarEstadoRequest {
  estado: Exclude<EstadoEncuesta, 'borrador'>;
}

// ── Votación / resultados ─────────────────────────────────────

export interface VotarRequest {
  opcionId: string;
}

export interface ResultadoOpcion {
  opcionId: string;
  texto: string;
  orden: number;
  totalVotos: number;
  porcentaje: number;
}

export interface ResultadoEncuesta {
  encuestaId: string;
  titulo: string;
  estado: EstadoEncuesta;
  totalVotos: number;
  opciones: ResultadoOpcion[];
}

// ── Errores ───────────────────────────────────────────────────

export interface ApiError {
  status: number;
  error: string;
  mensaje: string;
  timestamp: string;
  /** Solo en errores de validación (400). */
  campos?: Record<string, string>;
}
