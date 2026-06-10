package cibertec.edu.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Utilidad para generar, firmar y validar JWT.
 *
 * El token incluye:
 *   - sub  : UUID del usuario
 *   - email: email del usuario
 *   - rol  : "admin" o "usuario"
 *   - iat  : fecha de emisión
 *   - exp  : fecha de expiración
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {

        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.secretKey   = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // ── Generación ────────────────────────────────────────────

    public String generarToken(UUID usuarioId, String email, String rol) {
        Date ahora     = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(secretKey)
                .compact();
    }

    // ── Extracción ────────────────────────────────────────────

    public UUID extraerUsuarioId(String token) {
        return UUID.fromString(extraerClaims(token).getSubject());
    }

    public String extraerEmail(String token) {
        return extraerClaims(token).get("email", String.class);
    }

    public String extraerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    // ── Validación ────────────────────────────────────────────

    /**
     * Valida el token y lanza excepción si es inválido o expirado.
     * Las excepciones son capturadas por JwtAuthFilter.
     */
    public boolean esValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Interno ───────────────────────────────────────────────

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
