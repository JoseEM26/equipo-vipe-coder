package cibertec.edu.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Resultados en tiempo real de una encuesta.
 * Es el payload que se publica en /topic/encuesta/{id} tras cada voto.
 */
public record ResultadoEncuestaDto(
        UUID                     encuestaId,
        String                   titulo,
        String                   estado,
        long                     totalVotos,
        List<ResultadoOpcionDto> opciones
) {}
