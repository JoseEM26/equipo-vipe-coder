package cibertec.edu.repo.projection;

/**
 * Proyección por interfaz sobre la vista v_resultados_encuesta.
 * Los alias de la consulta nativa deben coincidir con los nombres de estos
 * getters (opcionId, texto, orden, totalVotos, porcentaje).
 */
public interface ResultadoOpcionView {
    String getOpcionId();
    String getTexto();
    int getOrden();
    long getTotalVotos();
    Double getPorcentaje();
}
