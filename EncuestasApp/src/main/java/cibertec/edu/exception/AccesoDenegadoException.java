package cibertec.edu.exception;

// 403 Forbidden (acción no permitida para el usuario actual según el estado
// del recurso; p. ej. ver resultados sin haber votado).
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) { super(mensaje); }
}
