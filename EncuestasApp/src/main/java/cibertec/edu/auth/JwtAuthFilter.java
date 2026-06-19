package cibertec.edu.auth;

import cibertec.edu.entity.UsuarioPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filtro que se ejecuta UNA VEZ por petición HTTP.
 *
 * Flujo:
 *   1. Extrae el token del header Authorization: Bearer <token>
 *   2. Valida el token con JwtUtil
 *   3. Carga el usuario en el SecurityContext
 *   4. Continúa la cadena de filtros
 *
 * Si el token es inválido o ausente, la petición continúa sin
 * autenticación y Spring Security la rechazará si el endpoint
 * requiere autenticación.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Sin header o sin prefijo Bearer → pasar sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // Quitar "Bearer "

        if (!jwtUtil.esValido(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer datos del token
        UUID   usuarioId = jwtUtil.extraerUsuarioId(token);
        String email     = jwtUtil.extraerEmail(token);
        String rol       = jwtUtil.extraerRol(token);

        // Construir la autenticación con el rol como authority
        // Spring Security espera el prefijo ROLE_ para @PreAuthorize("hasRole('ADMIN')")
        var authority = new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase());

        var authentication = new UsernamePasswordAuthenticationToken(
                new UsuarioPrincipal(usuarioId, email, rol),
                null,
                List.of(authority)
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Registrar en el contexto de seguridad
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
