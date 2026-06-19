package cibertec.edu.controllers;

import cibertec.edu.services.AuthService;
import cibertec.edu.dto.request.LoginRequest;
import cibertec.edu.dto.request.RegistroRequest;
import cibertec.edu.dto.response.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints públicos de autenticación.
 *
 *   POST /api/auth/login    → devuelve JWT
 *   POST /api/auth/registro → crea cuenta y devuelve JWT
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login de usuario existente.
     *
     * Body: { "email": "...", "password": "..." }
     * Response 200: AuthResponse con JWT
     * Response 401: Credenciales incorrectas
     * Response 400: Validación fallida (email malformado, etc.)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Registro de nuevo usuario (rol 'usuario' por defecto).
     *
     * Body: { "nombre": "...", "email": "...", "password": "..." }
     * Response 201: AuthResponse con JWT (el usuario queda logueado al registrarse)
     * Response 409: Email ya registrado
     * Response 400: Validación fallida
     */
    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registro(@Valid @RequestBody RegistroRequest request) {
        AuthResponse response = authService.registro(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
