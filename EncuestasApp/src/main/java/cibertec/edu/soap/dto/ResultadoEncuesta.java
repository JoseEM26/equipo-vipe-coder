package cibertec.edu.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

/** Resultados agregados de una encuesta en el contrato SOAP. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResultadoEncuesta")
public class ResultadoEncuesta {

    private String encuestaId;
    private String titulo;
    private String estado;
    private long   totalVotos;
    private List<ResultadoOpcion> opciones = new ArrayList<>();

    public String getEncuestaId() { return encuestaId; }
    public void setEncuestaId(String encuestaId) { this.encuestaId = encuestaId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public long getTotalVotos() { return totalVotos; }
    public void setTotalVotos(long totalVotos) { this.totalVotos = totalVotos; }

    public List<ResultadoOpcion> getOpciones() { return opciones; }
    public void setOpciones(List<ResultadoOpcion> opciones) { this.opciones = opciones; }
}
