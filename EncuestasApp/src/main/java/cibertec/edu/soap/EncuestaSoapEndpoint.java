package cibertec.edu.soap;

import cibertec.edu.dto.response.ResultadoEncuestaDto;
import cibertec.edu.dto.response.ResultadoOpcionDto;
import cibertec.edu.services.VotoService;
import cibertec.edu.soap.dto.ObtenerResultadosRequest;
import cibertec.edu.soap.dto.ObtenerResultadosResponse;
import cibertec.edu.soap.dto.ResultadoEncuesta;
import cibertec.edu.soap.dto.ResultadoOpcion;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint SOAP de resultados.
 *
 * Reutiliza {@link VotoService#resultadosPublicos} (la misma lógica de negocio
 * que la API REST) y solo se encarga de traducir entre los DTOs internos y los
 * DTOs JAXB del contrato (soap/encuestas.xsd).
 *
 * WSDL publicado en: /soap/encuestas.wsdl
 */
@Endpoint
@RequiredArgsConstructor
public class EncuestaSoapEndpoint {

    private static final String NAMESPACE = "http://votaciones.cibertec.edu/soap";

    private final VotoService votoService;

    @PayloadRoot(namespace = NAMESPACE, localPart = "ObtenerResultadosRequest")
    @ResponsePayload
    public ObtenerResultadosResponse obtenerResultados(@RequestPayload ObtenerResultadosRequest request) {
        ResultadoEncuestaDto dto =
                votoService.resultadosPublicos(UUID.fromString(request.getEncuestaId()));

        ObtenerResultadosResponse response = new ObtenerResultadosResponse();
        response.setResultado(mapResultado(dto));
        return response;
    }

    private static ResultadoEncuesta mapResultado(ResultadoEncuestaDto dto) {
        ResultadoEncuesta r = new ResultadoEncuesta();
        r.setEncuestaId(dto.encuestaId().toString());
        r.setTitulo(dto.titulo());
        r.setEstado(dto.estado());
        r.setTotalVotos(dto.totalVotos());
        r.setOpciones(mapOpciones(dto.opciones()));
        return r;
    }

    private static List<ResultadoOpcion> mapOpciones(List<ResultadoOpcionDto> opciones) {
        return opciones.stream().map(o -> {
            ResultadoOpcion ro = new ResultadoOpcion();
            ro.setOpcionId(o.opcionId().toString());
            ro.setTexto(o.texto());
            ro.setOrden(o.orden());
            ro.setTotalVotos(o.totalVotos());
            ro.setPorcentaje(o.porcentaje());
            return ro;
        }).toList();
    }
}
