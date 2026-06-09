package cibertec.edu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración del broker STOMP sobre WebSocket.
 *
 * Canales definidos:
 *
 *   SUSCRIPCIÓN (cliente escucha):
 *     /topic/encuesta/{id}  → resultados en tiempo real de una encuesta
 *
 *   ENVÍO (cliente → servidor):
 *     No se usa en la Opción A. Los votos llegan por REST (POST /api/encuestas/{id}/votar).
 *     El servidor usa SimpMessagingTemplate para publicar en /topic/* después de cada voto.
 *
 *   HANDSHAKE:
 *     ws://servidor/ws          (WebSocket nativo)
 *     http://servidor/ws        (SockJS fallback para navegadores sin WS)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broker simple en memoria para el canal /topic
        // En producción con múltiples instancias, reemplazar por broker externo (RabbitMQ/Redis)
        registry.enableSimpleBroker("/topic");

        // Prefijo para mensajes que van a @MessageMapping (no usado en Opción A)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // En producción: reemplazar "*" por el dominio del frontend
                .setAllowedOriginPatterns("*")
                // SockJS permite fallback HTTP en navegadores que no soporten WebSocket
                .withSockJS();
    }
}
