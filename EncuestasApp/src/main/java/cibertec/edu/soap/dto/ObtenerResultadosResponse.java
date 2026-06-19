package cibertec.edu.soap.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Respuesta SOAP: resultados agregados de la encuesta. */
@XmlRootElement(name = "ObtenerResultadosResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class ObtenerResultadosResponse {

    private ResultadoEncuesta resultado;

    public ResultadoEncuesta getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoEncuesta resultado) {
        this.resultado = resultado;
    }
}
