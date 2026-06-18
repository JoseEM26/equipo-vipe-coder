package cibertec.edu.entity;

/**
 * Estados del ciclo de vida de una encuesta.
 *
 * Transiciones permitidas:
 *   borrador → activa | cancelada
 *   activa   → finalizada | cancelada
 *
 * 'finalizada' y 'cancelada' son estados terminales: no se puede salir
 * de ellos. El valor de BD se almacena en minúsculas (columna ENUM de MySQL).
 */
public enum EstadoEncuesta {
    BORRADOR("borrador"),
    ACTIVA("activa"),
    FINALIZADA("finalizada"),
    CANCELADA("cancelada");

    private final String valor;

    EstadoEncuesta(String valor) {
        this.valor = valor;
    }

    /** Valor tal como se guarda en la columna ENUM de MySQL. */
    public String valor() {
        return valor;
    }

    /** Convierte el valor de BD al enum. Lanza IllegalArgumentException si no existe. */
    public static EstadoEncuesta desde(String valor) {
        for (EstadoEncuesta e : values()) {
            if (e.valor.equals(valor)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Estado de encuesta desconocido: " + valor);
    }

    /**
     * Indica si es válido pasar de este estado al destino.
     *   borrador → activa | cancelada
     *   activa   → finalizada | cancelada
     */
    public boolean puedeTransicionarA(EstadoEncuesta destino) {
        return (this == BORRADOR && (destino == ACTIVA || destino == CANCELADA))
                || (this == ACTIVA && (destino == FINALIZADA || destino == CANCELADA));
    }
}
