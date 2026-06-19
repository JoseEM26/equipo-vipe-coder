package cibertec.edu.controllers;

import cibertec.edu.dto.request.VotarRequest;
import cibertec.edu.dto.response.ResultadoEncuestaDto;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.services.VotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoints de votación y resultados.
 *
 *   POST /api/encuestas/{id}/votar       emitir voto (autenticado)
 *   GET  /api/encuestas/{id}/resultados  resultados agregados (autenticado)
 *
 * Tras cada voto, los resultados también se publican en
 * /topic/encuesta/{id} para los clientes suscritos por WebSocket.
 */
@RestController
@RequestMapping("/api/encuestas/{encuestaId}")
@RequiredArgsConstructor
public class VotoController {

    private final VotoService votoService;

    @PostMapping("/votar")
    public ResponseEntity<ResultadoEncuestaDto> votar(
            @PathVariable UUID encuestaId,
            @Valid @RequestBody VotarRequest request,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        ResultadoEncuestaDto resultado =
                votoService.votar(encuestaId, request.getOpcionId(), principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    @GetMapping("/resultados")
    public ResultadoEncuestaDto resultados(
            @PathVariable UUID encuestaId,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return votoService.resultados(encuestaId, principal);
    }
}
