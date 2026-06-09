package cibertec.edu.auth;

import java.util.UUID;

/**
 * Objeto que representa al usuario autenticado dentro del SecurityContext.
 *
 * Se accede desde cualquier controller con:
 *   @AuthenticationPrincipal UsuarioPrincipal principal
 *
 * Ejemplo:
 *   public ResponseEntity<?> votar(@AuthenticationPrincipal UsuarioPrincipal principal) {
 *       UUID usuarioId = principal.id();
 *       String rol     = principal.rol();
 *   }
 */
public record UsuarioPrincipal(
        UUID   id,
        String email,
        String rol
) {}
