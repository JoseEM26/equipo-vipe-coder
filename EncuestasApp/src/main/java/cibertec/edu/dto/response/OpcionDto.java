package cibertec.edu.dto.response;

import java.util.UUID;

/** Una opción de respuesta dentro de una encuesta. */
public record OpcionDto(
        UUID   id,
        String texto,
        int    orden
) {}
