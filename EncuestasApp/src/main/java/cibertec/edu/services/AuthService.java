package cibertec.edu.services;

import cibertec.edu.auth.JwtUtil;
import cibertec.edu.dto.request.LoginRequest;
import cibertec.edu.dto.request.RegistroRequest;
import cibertec.edu.dto.response.AuthResponse;
import cibertec.edu.exception.ConflictoException;
import cibertec.edu.exception.CredencialesInvalidasException;
import cibertec.edu.repo.AuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // ── Login ─────────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        // 1. Buscar usuario por email
        AuthRepository.UsuarioAuth usuario = authRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new CredencialesInvalidasException(
                        "Email o contraseña incorrectos"));
                        // Mensaje genérico intencionalmente: no revelar si el email existe

        // 2. Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), usuario.passwordHash())) {
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }

        // 3. Generar token y devolver respuesta
        return construirRespuesta(usuario.id(), usuario.nombre(), usuario.email(), usuario.rol());
    }

    // ── Registro ──────────────────────────────────────────────

    public AuthResponse registro(RegistroRequest request) {
        // 1. Verificar que el email no esté en uso
        if (authRepository.existeEmail(request.getEmail())) {
            throw new ConflictoException("El email ya está registrado");
        }

        // 2. Hashear contraseña
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 3. Persistir usuario
        UUID nuevoId = authRepository.registrar(
                request.getNombre(),
                request.getEmail(),
                passwordHash
        );

        return construirRespuesta(nuevoId, request.getNombre(), request.getEmail(), "usuario");
    }

    // ── Interno ───────────────────────────────────────────────

    private AuthResponse construirRespuesta(UUID id, String nombre, String email, String rol) {
        String token     = jwtUtil.generarToken(id, email, rol);
        long   expiraEn  = System.currentTimeMillis() + expirationMs;

        return new AuthResponse(token, id, nombre, email, rol, expiraEn);
    }
}
