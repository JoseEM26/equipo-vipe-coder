package cibertec.edu.exception;

import cibertec.edu.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manejador central de excepciones.
 *
 * Captura excepciones de dominio y errores de la base de datos (MySQL)
 * y los convierte en respuestas HTTP con el formato estándar ErrorResponse.
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

    // ── Errores de integridad de datos (constraints MySQL) ────
    // Las reglas de negocio se validan en los services; aquí solo quedan
    // las violaciones de constraints que la BD sigue garantizando como
    // última línea de defensa (UNIQUE y FOREIGN KEY).

    // UNIQUE violation → email o voto duplicado.
    // Spring traduce el error 1062 de MySQL a DuplicateKeyException.
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicado(DuplicateKeyException e) {
        log.warn("Violación de UNIQUE: {}", e.getMostSpecificCause().getMessage());

        String causa = e.getMostSpecificCause().getMessage();
        String mensaje;
        if (causa != null && causa.contains("uq_votos_usuario_encuesta")) {
            mensaje = "Ya has votado en esta encuesta";
        } else if (causa != null && causa.contains("uq_usuarios_email")) {
            mensaje = "El email ya está registrado";
        } else {
            mensaje = "Ya existe un registro con esos datos";
        }

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "Conflicto", mensaje));
    }

    // FOREIGN KEY u otra violación de integridad → referencia inválida.
    // (DuplicateKeyException es subclase, por eso se maneja antes.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridad(DataIntegrityViolationException e) {
        log.error("Violación de integridad: {}", e.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Referencia inválida",
                        "Uno de los recursos referenciados no existe o los datos no son válidos"));
    }

    // ── Errores explícitos de triggers MySQL (SIGNAL '45000') ──
    // Los triggers actúan como última línea de defensa de las reglas de
    // negocio (transiciones de estado, voto en encuesta activa, etc.).
    // MySQL no mapea el SQLState 45000 a una excepción de Spring concreta,
    // por lo que llega como UncategorizedSQLException. El MESSAGE_TEXT del
    // SIGNAL viaja en el mensaje de la SQLException original.
    @ExceptionHandler(UncategorizedSQLException.class)
    public ResponseEntity<ErrorResponse> handleSqlNoCategorizado(UncategorizedSQLException e) {
        SQLException sql = e.getSQLException();
        String sqlState = sql != null ? sql.getSQLState() : null;

        if ("45000".equals(sqlState)) {
            String mensaje = sql.getMessage();
            log.warn("Regla de negocio rechazada por trigger: {}", mensaje);
            return ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ErrorResponse(422, "Operación no permitida",
                            mensaje != null ? mensaje
                                            : "La operación viola una regla de negocio"));
        }

        log.error("Error SQL no categorizado [{}]: {}", sqlState, e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Error de base de datos",
                        "Error inesperado en la base de datos"));
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
