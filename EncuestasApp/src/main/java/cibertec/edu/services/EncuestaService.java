package cibertec.edu.services;

import cibertec.edu.dto.request.EncuestaRequest;
import cibertec.edu.dto.response.EncuestaDto;
import cibertec.edu.dto.response.EncuestaResumenDto;
import cibertec.edu.dto.response.OpcionDto;
import cibertec.edu.entity.EstadoEncuesta;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.exception.EstadoInvalidoException;
import cibertec.edu.exception.RecursoNoEncontradoException;
import cibertec.edu.repo.EncuestaRepository;
import cibertec.edu.repo.EncuestaRepository.EncuestaRow;
import cibertec.edu.repo.VotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio de encuestas.
 *
 * Aquí viven las reglas que antes estaban en triggers PostgreSQL:
 *   - solo se editan/eliminan encuestas en borrador,
 *   - las transiciones de estado solo avanzan (borrador→activa→finalizada).
 * Los triggers de MySQL las respaldan como última línea de defensa.
 */
@Service
@RequiredArgsConstructor
public class EncuestaService {

    private final EncuestaRepository encuestaRepo;
    private final VotoRepository     votoRepo;

    // ── Comandos (admin) ──────────────────────────────────────

    @Transactional
    public EncuestaDto crear(EncuestaRequest req, UUID adminId) {
        UUID id = encuestaRepo.crear(req.getTitulo(), req.getDescripcion(), adminId);
        guardarOpciones(id, req.getOpciones());
        return obtenerDto(id, null);
    }

    @Transactional
    public EncuestaDto actualizar(UUID id, EncuestaRequest req) {
        EncuestaRow row = cargar(id);
        exigirBorrador(row, "Solo se pueden editar encuestas en borrador");

        encuestaRepo.actualizarDatos(id, req.getTitulo(), req.getDescripcion());
        encuestaRepo.borrarOpciones(id);
        guardarOpciones(id, req.getOpciones());
        return obtenerDto(id, null);
    }

    @Transactional
    public EncuestaDto cambiarEstado(UUID id, String nuevoEstadoStr) {
        EncuestaRow row = cargar(id);
        EstadoEncuesta actual  = EstadoEncuesta.desde(row.estado());
        EstadoEncuesta destino = parsearEstado(nuevoEstadoStr);

        if (actual == destino) {
            return obtenerDto(id, null);   // sin cambios
        }
        if (!actual.puedeTransicionarA(destino)) {
            throw new EstadoInvalidoException(
                    "Transición no permitida: " + actual.valor() + " → " + destino.valor()
                            + ". Flujo válido: borrador → activa → finalizada;"
                            + " se puede cancelar desde borrador o activa.");
        }
        encuestaRepo.cambiarEstado(id, destino.valor());
        return obtenerDto(id, null);
    }

    @Transactional
    public void borrar(UUID id) {
        EncuestaRow row = cargar(id);
        exigirBorrador(row, "Solo se pueden eliminar encuestas en borrador");
        encuestaRepo.borrar(id);
    }

    // ── Consultas ─────────────────────────────────────────────

    public List<EncuestaResumenDto> listarTodas() {
        return encuestaRepo.listarTodas();
    }

    public List<EncuestaResumenDto> listarActivas(UUID usuarioId) {
        return encuestaRepo.listarActivas(usuarioId);
    }

    public List<EncuestaResumenDto> listarFinalizadas(UsuarioPrincipal principal) {
        return esAdmin(principal)
                ? encuestaRepo.listarFinalizadasTodas()
                : encuestaRepo.listarFinalizadasDeUsuario(principal.id());
    }

    public EncuestaDto obtenerDetalle(UUID id, UsuarioPrincipal principal) {
        EncuestaRow row = cargar(id);

        // Un usuario no-admin no puede ver borradores: se oculta como inexistente.
        if (!esAdmin(principal) && EstadoEncuesta.BORRADOR.valor().equals(row.estado())) {
            throw new RecursoNoEncontradoException("Encuesta no encontrada");
        }

        Boolean yaVoto = esAdmin(principal) ? null : votoRepo.yaVoto(id, principal.id());
        return mapDto(row, encuestaRepo.opcionesDe(id), yaVoto);
    }

    // ── Internos ──────────────────────────────────────────────

    private EncuestaDto obtenerDto(UUID id, Boolean yaVoto) {
        EncuestaRow row = cargar(id);
        return mapDto(row, encuestaRepo.opcionesDe(id), yaVoto);
    }

    private EncuestaRow cargar(UUID id) {
        return encuestaRepo.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));
    }

    private void guardarOpciones(UUID encuestaId, List<String> opciones) {
        int orden = 0;
        for (String texto : opciones) {
            encuestaRepo.crearOpcion(encuestaId, texto.trim(), orden++);
        }
    }

    private static void exigirBorrador(EncuestaRow row, String mensaje) {
        if (!EstadoEncuesta.BORRADOR.valor().equals(row.estado())) {
            throw new EstadoInvalidoException(mensaje);
        }
    }

    private static EstadoEncuesta parsearEstado(String valor) {
        try {
            return EstadoEncuesta.desde(valor);
        } catch (IllegalArgumentException e) {
            throw new EstadoInvalidoException("Estado inválido: '" + valor
                    + "'. Valores válidos: borrador, activa, finalizada.");
        }
    }

    private static boolean esAdmin(UsuarioPrincipal principal) {
        return "admin".equalsIgnoreCase(principal.rol());
    }

    private static EncuestaDto mapDto(EncuestaRow r, List<OpcionDto> opciones, Boolean yaVoto) {
        return new EncuestaDto(
                r.id(), r.titulo(), r.descripcion(), r.estado(),
                r.creadoEn(), r.activadaEn(), r.finalizadaEn(), r.canceladaEn(),
                opciones, yaVoto);
    }
}
