package cibertec.edu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Formato estándar de error para todas las respuestas de error de la API.
 *
 * Ejemplo de respuesta 400:
 * {
 *   "status": 400,
 *   "error": "Validación fallida",
 *   "mensaje": "Hay errores en los campos enviados",
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "campos": {
 *     "email": "Formato de email inválido",
 *     "password": "La contraseña debe tener al menos 8 caracteres"
 *   }
 * }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // No incluir campos null en el JSON
public record ErrorResponse(
        int              status,
        String           error,
        String           mensaje,
        Instant          timestamp,
        Map<String, String> campos   // Solo presente en errores de validación
) {
    // Constructor sin campos (para errores simples)
    public ErrorResponse(int status, String error, String mensaje) {
        this(status, error, mensaje, Instant.now(), null);
    }

    // Constructor con campos (para errores de validación)
    public ErrorResponse(int status, String error, String mensaje, Map<String, String> campos) {
        this(status, error, mensaje, Instant.now(), campos);
    }
}
