package cibertec.edu.services;

import cibertec.edu.auth.JwtUtil;
import cibertec.edu.dto.request.LoginRequest;
import cibertec.edu.dto.request.RegistroRequest;
import cibertec.edu.dto.response.AuthResponse;
import cibertec.edu.entity.Usuario;
import cibertec.edu.exception.ConflictoException;
import cibertec.edu.exception.CredencialesInvalidasException;
import cibertec.edu.repo.UsuarioRepository;
import cibertec.edu.repo.projection.UsuarioAuthView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // ── Login ─────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        // 1. Buscar usuario activo por email (proyección por interfaz)
        UsuarioAuthView usuario = usuarioRepository
                .findByEmailAndActivoTrue(request.getEmail())
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "Email o contraseña incorrectos"));
                        // Mensaje genérico intencionalmente: no revelar si el email existe

        // 2. Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }

        // 3. Generar token y devolver respuesta
        return construirRespuesta(usuario.getId(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }

    // ── Registro ──────────────────────────────────────────────

    public AuthResponse registro(RegistroRequest request) {
        // 1. Verificar que el email no esté en uso
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new ConflictoException("El email ya está registrado");
        }

        // 2. Hashear contraseña y persistir (rol 'usuario' por defecto)
        Usuario usuario = new Usuario(
                request.getNombre(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()));
        Usuario guardado = usuarioRepository.save(usuario);

        return construirRespuesta(guardado.getId(), guardado.getNombre(),
                guardado.getEmail(), guardado.getRol());
    }

    // ── Interno ───────────────────────────────────────────────

    private AuthResponse construirRespuesta(UUID id, String nombre, String email, String rol) {
        String token     = jwtUtil.generarToken(id, email, rol);
        long   expiraEn  = System.currentTimeMillis() + expirationMs;

        return new AuthResponse(token, id, nombre, email, rol, expiraEn);
    }
}
