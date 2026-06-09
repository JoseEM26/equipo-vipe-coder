package cibertec.edu.exception;

// 422 Unprocessable Entity (transición de estado inválida, voto en encuesta no activa, etc.)
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensaje) { super(mensaje); }
}
