package cibertec.edu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resumen de encuesta para listados (panel admin, encuestas activas,
 * encuestas finalizadas).
 *
 * yaVoto: si el usuario actual ya participó. Null cuando no aplica
 * (listados de admin).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncuestaResumenDto(
        UUID          id,
        String        titulo,
        String        descripcion,
        String        estado,
        LocalDateTime creadoEn,
        int           totalOpciones,
        long          totalVotos,
        Boolean       yaVoto
) {}
