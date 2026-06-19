package cibertec.edu.repo.projection;

import java.util.UUID;

/**
 * Proyección por interfaz para conteos agregados por encuesta
 * (total de opciones o total de votos), evitando consultas N+1 en los listados.
 */
public interface ConteoView {
    UUID getId();
    long getTotal();
}
