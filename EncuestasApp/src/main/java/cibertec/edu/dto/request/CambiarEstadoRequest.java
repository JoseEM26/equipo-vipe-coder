package cibertec.edu.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo para cambiar el estado de una encuesta (solo admin).
 * Valores válidos: "activa", "finalizada".
 * La transición se valida en el service (solo se permite avanzar).
 */
public class CambiarEstadoRequest {

    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    public String getEstado()              { return estado; }
    public void   setEstado(String estado) { this.estado = estado; }
}
