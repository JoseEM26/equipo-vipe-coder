package cibertec.edu.config;

import cibertec.edu.auth.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 *     /topic/admin/encuesta/{id}  → resultados en tiempo real (SOLO admins).
 *         La autorización la aplica WebSocketAuthInterceptor en el SUBSCRIBE.
 *         Los usuarios normales NO reciben el feed en vivo; obtienen los
 *         resultados por REST tras votar o cuando la encuesta finaliza.
 *
 *   ENVÍO (cliente → servidor):
 *     No se usa. Los votos llegan por REST (POST /api/encuestas/{id}/votar) y
 *     el servidor publica con SimpMessagingTemplate tras cada voto.
 *
 *   HANDSHAKE:
 *     ws://servidor/ws          (WebSocket nativo)
 *     http://servidor/ws        (SockJS fallback)
 *     El handshake es público; la autenticación se valida en el CONNECT STOMP
 *     (header "Authorization: Bearer <token>") vía WebSocketAuthInterceptor.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Aplica autenticación (CONNECT) y autorización (SUBSCRIBE) a los frames entrantes.
        registration.interceptors(webSocketAuthInterceptor);
    }

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
