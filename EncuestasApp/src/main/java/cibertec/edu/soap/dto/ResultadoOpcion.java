package cibertec.edu.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/** Resultado agregado de una opción en el contrato SOAP. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResultadoOpcion")
public class ResultadoOpcion {

    private String opcionId;
    private String texto;
    private int    orden;
    private long   totalVotos;
    private double porcentaje;

    public String getOpcionId() { return opcionId; }
    public void setOpcionId(String opcionId) { this.opcionId = opcionId; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public long getTotalVotos() { return totalVotos; }
    public void setTotalVotos(long totalVotos) { this.totalVotos = totalVotos; }

    public double getPorcentaje() { return porcentaje; }
    public void setPorcentaje(double porcentaje) { this.porcentaje = porcentaje; }
}
