package cibertec.edu.exception;

// 409 Conflict (email duplicado, voto duplicado, etc.)
public class ConflictoException extends RuntimeException {
    public ConflictoException(String mensaje) { super(mensaje); }
}
