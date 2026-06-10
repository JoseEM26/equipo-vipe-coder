package cibertec.edu.exception;

// ── Excepciones de dominio ────────────────────────────────────────────────────
// Cada excepción mapea a un HTTP status específico en GlobalExceptionHandler.
// Son unchecked (RuntimeException) para no contaminar las firmas de los métodos.

// 401 Unauthorized
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException(String mensaje) { super(mensaje); }
}
