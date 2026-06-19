package cibertec.edu.auth;

import cibertec.edu.entity.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Autenticación y autorización a nivel STOMP (WebSocket).
 *
 * El handshake HTTP de /ws es público (SockJS no transporta el token con
 * facilidad), por lo que la seguridad se aplica aquí:
 *
 *   CONNECT   → exige un JWT válido en el header nativo "Authorization:
 *               Bearer <token>" y fija el usuario en la sesión STOMP.
 *   SUBSCRIBE → el canal en tiempo real /topic/admin/** queda restringido
 *               a administradores. El resto de usuarios autenticados puede
 *               suscribirse a otros destinos.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String ADMIN_DESTINO_PREFIX = "/topic/admin/";
    private static final String ROL_ADMIN            = "ROLE_ADMIN";

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT   -> autenticar(accessor);
            case SUBSCRIBE -> autorizarSuscripcion(accessor);
            default        -> { /* otros frames no requieren chequeo */ }
        }
        return message;
    }

    /** Valida el JWT del CONNECT y deja al usuario autenticado en la sesión. */
    private void autenticar(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Falta el token de autenticación");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.esValido(token)) {
            throw new MessagingException("Token inválido o expirado");
        }

        UUID   usuarioId = jwtUtil.extraerUsuarioId(token);
        String email     = jwtUtil.extraerEmail(token);
        String rol        = jwtUtil.extraerRol(token);

        var authority = new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase());
        var authentication = new UsernamePasswordAuthenticationToken(
                new UsuarioPrincipal(usuarioId, email, rol),
                null,
                List.of(authority));

        accessor.setUser(authentication);
    }

    /** El feed en tiempo real (/topic/admin/**) solo es para administradores. */
    private void autorizarSuscripcion(StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        if (destino != null && destino.startsWith(ADMIN_DESTINO_PREFIX) && !esAdmin(accessor.getUser())) {
            throw new MessagingException(
                    "Solo los administradores pueden ver los resultados en tiempo real");
        }
    }

    private static boolean esAdmin(Principal user) {
        return user instanceof UsernamePasswordAuthenticationToken token
                && token.getAuthorities().stream()
                        .anyMatch(a -> ROL_ADMIN.equals(a.getAuthority()));
    }
}
