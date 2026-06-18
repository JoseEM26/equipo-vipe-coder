package cibertec.edu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cuerpo para crear o editar una encuesta (solo admin, solo en borrador).
 *
 * Las opciones se reciben como una lista de textos; el orden se asigna
 * según la posición en la lista (0-based).
 */
public class EncuestaRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255, message = "El título no puede superar 255 caracteres")
    private String titulo;

    @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
    private String descripcion;

    @NotNull(message = "Las opciones son obligatorias")
    @Size(min = 2, message = "La encuesta debe tener al menos 2 opciones")
    private List<
            @NotBlank(message = "Las opciones no pueden estar vacías")
            @Size(max = 500, message = "Cada opción no puede superar 500 caracteres")
                    String> opciones;

    public String getTitulo()                    { return titulo; }
    public void   setTitulo(String titulo)        { this.titulo = titulo; }
    public String getDescripcion()                { return descripcion; }
    public void   setDescripcion(String d)        { this.descripcion = d; }
    public List<String> getOpciones()             { return opciones; }
    public void   setOpciones(List<String> o)     { this.opciones = o; }
}
