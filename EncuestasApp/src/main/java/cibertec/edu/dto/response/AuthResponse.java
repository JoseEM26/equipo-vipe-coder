package cibertec.edu.dto.response;

import java.util.UUID;

/**
 * Respuesta devuelta al cliente tras un login o registro exitoso.
 * El cliente debe guardar el token y enviarlo en cada petición:
 *   Authorization: Bearer <token>
 */
public record AuthResponse(
        String token,
        UUID   usuarioId,
        String nombre,
        String email,
        String rol,
        long   expiraEn    // Timestamp Unix en ms cuando expira el token
) {}
