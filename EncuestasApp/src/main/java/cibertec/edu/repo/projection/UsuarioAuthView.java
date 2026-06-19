package cibertec.edu.repo.projection;

import java.util.UUID;

/**
 * Proyección por interfaz (Spring Data) con los campos mínimos para autenticar.
 * Spring genera un proxy que solo selecciona estas columnas, sin cargar la
 * entidad Usuario completa.
 */
public interface UsuarioAuthView {
    UUID getId();
    String getNombre();
    String getEmail();
    String getPasswordHash();
    String getRol();
}
