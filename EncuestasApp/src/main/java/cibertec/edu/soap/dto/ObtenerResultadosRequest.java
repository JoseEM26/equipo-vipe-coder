package cibertec.edu.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Petición SOAP: identificador de la encuesta a consultar. */
@XmlRootElement(name = "ObtenerResultadosRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class ObtenerResultadosRequest {

    private String encuestaId;

    public String getEncuestaId() {
        return encuestaId;
    }

    public void setEncuestaId(String encuestaId) {
        this.encuestaId = encuestaId;
    }
}
