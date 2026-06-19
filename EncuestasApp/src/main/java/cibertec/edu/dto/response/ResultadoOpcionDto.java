package cibertec.edu.dto.response;

import java.util.UUID;

/** Resultado agregado de una opción: total de votos y porcentaje. */
public record ResultadoOpcionDto(
        UUID   opcionId,
        String texto,
        int    orden,
        long   totalVotos,
        double porcentaje
) {}
