package cibertec.edu.controllers;

import cibertec.edu.dto.request.CambiarEstadoRequest;
import cibertec.edu.dto.request.EncuestaRequest;
import cibertec.edu.dto.response.EncuestaDto;
import cibertec.edu.dto.response.EncuestaResumenDto;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.services.EncuestaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de encuestas.
 *
 * Admin (ROLE_ADMIN):
 *   POST   /api/encuestas             crear (borrador)
 *   PUT    /api/encuestas/{id}        editar (solo borrador)
 *   PATCH  /api/encuestas/{id}/estado cambiar estado (avanzar)
 *   DELETE /api/encuestas/{id}        eliminar (solo borrador)
 *   GET    /api/encuestas             listar todas (panel admin)
 *
 * Autenticados (admin o usuario):
 *   GET    /api/encuestas/activas      activas disponibles (con "ya votó")
 *   GET    /api/encuestas/finalizadas  admin: todas; usuario: solo las suyas
 *   GET    /api/encuestas/{id}         detalle con opciones
 */
@RestController
@RequestMapping("/api/encuestas")
@RequiredArgsConstructor
public class EncuestaController {

    private final EncuestaService encuestaService;

    // ── Admin ─────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EncuestaDto> crear(
            @Valid @RequestBody EncuestaRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        EncuestaDto creada = encuestaService.crear(request, principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public EncuestaDto actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EncuestaRequest request) {
        return encuestaService.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public EncuestaDto cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return encuestaService.cambiarEstado(id, request.getEstado());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> borrar(@PathVariable UUID id) {
        encuestaService.borrar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<EncuestaResumenDto> listarTodas() {
        return encuestaService.listarTodas();
    }

    // ── Autenticados (admin o usuario) ────────────────────────

    @GetMapping("/activas")
    public List<EncuestaResumenDto> activas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return encuestaService.listarActivas(principal.id());
    }

    @GetMapping("/finalizadas")
    public List<EncuestaResumenDto> finalizadas(@AuthenticationPrincipal UsuarioPrincipal principal) {
        return encuestaService.listarFinalizadas(principal);
    }

    @GetMapping("/{id}")
    public EncuestaDto detalle(
            @PathVariable UUID id,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return encuestaService.obtenerDetalle(id, principal);
    }
}
