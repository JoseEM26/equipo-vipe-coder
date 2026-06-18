package cibertec.edu.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Cuerpo para emitir un voto.
 * El id de la encuesta viaja en la URL; aquí solo la opción elegida.
 */
public class VotarRequest {

    @NotNull(message = "Debe indicar la opción a votar")
    private UUID opcionId;

    public UUID getOpcionId()              { return opcionId; }
    public void setOpcionId(UUID opcionId) { this.opcionId = opcionId; }
}
