/**
 * DTOs JAXB del servicio SOAP. El namespace y la forma calificada se declaran
 * aquí para que coincidan con el contrato (soap/encuestas.xsd) sin tener que
 * repetir el namespace en cada campo.
 */
@jakarta.xml.bind.annotation.XmlSchema(
        namespace = "http://votaciones.cibertec.edu/soap",
        elementFormDefault = jakarta.xml.bind.annotation.XmlNsForm.QUALIFIED)
package cibertec.edu.soap.dto;
