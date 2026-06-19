package cibertec.edu.config;

import cibertec.edu.soap.dto.ObtenerResultadosRequest;
import cibertec.edu.soap.dto.ObtenerResultadosResponse;
import cibertec.edu.soap.dto.ResultadoEncuesta;
import cibertec.edu.soap.dto.ResultadoOpcion;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Configuración del servicio SOAP (Spring Web Services, contract-first).
 *
 * - El {@link MessageDispatcherServlet} se mapea en /soap/* (separado del /ws
 *   del WebSocket para evitar colisión).
 * - El WSDL se genera automáticamente desde soap/encuestas.xsd y queda
 *   publicado en /soap/encuestas.wsdl.
 * - El {@link Jaxb2Marshaller} (con las clases JAXB ligadas explícitamente)
 *   resuelve el (un)marshalling de los @RequestPayload / @ResponsePayload.
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    private static final String NAMESPACE = "http://votaciones.cibertec.edu/soap";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/soap/*");
    }

    @Bean(name = "encuestas")
    public DefaultWsdl11Definition wsdlDefinition(XsdSchema encuestasSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("EncuestasPort");
        wsdl.setLocationUri("/soap");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(encuestasSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema encuestasSchema() {
        return new SimpleXsdSchema(new ClassPathResource("soap/encuestas.xsd"));
    }

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                ObtenerResultadosRequest.class,
                ObtenerResultadosResponse.class,
                ResultadoEncuesta.class,
                ResultadoOpcion.class);
        return marshaller;
    }
}
