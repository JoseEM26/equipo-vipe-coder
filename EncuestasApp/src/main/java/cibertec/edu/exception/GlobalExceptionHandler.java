package cibertec.edu.exception;

import cibertec.edu.dto.response.ErrorResponse;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador central de excepciones.
 *
 * Captura excepciones de dominio y errores de PostgreSQL y los convierte
 * en respuestas HTTP con el formato estándar ErrorResponse.
 *
 * El cliente siempre recibe JSON con la misma estructura, nunca stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Excepciones de dominio ────────────────────────────────

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> handleCredenciales(CredencialesInvalidasException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "No autorizado", e.getMessage()));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> handleConflicto(ConflictoException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "Conflicto", e.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNoEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "No encontrado", e.getMessage()));
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleEstadoInvalido(EstadoInvalidoException e) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(422, "Estado inválido", e.getMessage()));
    }

    // ── Validación de DTOs ────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Inválido",
                        (msg1, msg2) -> msg1  // Si hay dos errores en el mismo campo, tomar el primero
                ));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Validación fallida",
                        "Hay errores en los campos enviados", campos));
    }

    // ── Errores de PostgreSQL ─────────────────────────────────

    @ExceptionHandler(PSQLException.class)
    public ResponseEntity<ErrorResponse> handlePsql(PSQLException e) {
        String sqlState = e.getSQLState();
        log.error("PSQLException [{}]: {}", sqlState, e.getMessage());

        return switch (sqlState) {

            // unique_violation → voto duplicado u otro UNIQUE constraint
            case "23505" -> {
                String mensaje = e.getServerErrorMessage() != null
                        && e.getServerErrorMessage().getConstraint() != null
                        && e.getServerErrorMessage().getConstraint()
                           .contains("uq_votos_usuario_encuesta")
                        ? "Ya has votado en esta encuesta"
                        : "Ya existe un registro con esos datos";

                yield ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(409, "Conflicto", mensaje));
            }

            // foreign_key_violation → referencia a recurso inexistente
            case "23503" -> ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "Referencia inválida",
                            "Uno de los recursos referenciados no existe"));

            // check_violation → CHECK constraint fallido
            case "23514" -> ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(400, "Datos inválidos",
                            "Los datos enviados no cumplen las reglas de negocio"));

            // raise_exception → error explícito de un trigger PostgreSQL
            // El mensaje viene directamente del RAISE EXCEPTION del trigger
            case "P0001" -> {
                String mensajeTrigger = e.getServerErrorMessage() != null
                        ? e.getServerErrorMessage().getMessage()
                        : "Operación no permitida";

                yield ResponseEntity
                        .status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new ErrorResponse(422, "Operación no permitida", mensajeTrigger));
            }

            // serialization_failure / deadlock → errores transitorios, el cliente puede reintentar
            case "40001", "40P01" -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, "Conflicto temporal",
                            "Operación en conflicto con otra simultánea, intenta de nuevo"));

            // Cualquier otro error de BD
            default -> ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Error de base de datos",
                            "Error inesperado en la base de datos"));
        };
    }

    // ── Fallback general ──────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        // Loguear con stack trace completo para investigación
        log.error("Error inesperado: {}", e.getMessage(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Error interno",
                        "Ocurrió un error inesperado, por favor intenta más tarde"));
    }
}
