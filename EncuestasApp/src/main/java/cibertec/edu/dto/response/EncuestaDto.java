package cibertec.edu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de una encuesta con sus opciones.
 *
 * yaVoto: indica si el usuario actual ya votó. Es null cuando no aplica
 * (por ejemplo, cuando lo consulta un admin).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncuestaDto(
        UUID            id,
        String          titulo,
        String          descripcion,
        String          estado,
        LocalDateTime   creadoEn,
        LocalDateTime   activadaEn,
        LocalDateTime   finalizadaEn,
        LocalDateTime   canceladaEn,
        List<OpcionDto> opciones,
        Boolean         yaVoto
) {}
