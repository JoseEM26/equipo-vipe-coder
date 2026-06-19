package cibertec.edu.services;

import cibertec.edu.dto.request.EncuestaRequest;
import cibertec.edu.dto.response.EncuestaDto;
import cibertec.edu.dto.response.EncuestaResumenDto;
import cibertec.edu.dto.response.OpcionDto;
import cibertec.edu.entity.Encuesta;
import cibertec.edu.entity.EstadoEncuesta;
import cibertec.edu.entity.Opcion;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.exception.EstadoInvalidoException;
import cibertec.edu.exception.RecursoNoEncontradoException;
import cibertec.edu.repo.EncuestaRepository;
import cibertec.edu.repo.OpcionRepository;
import cibertec.edu.repo.VotoRepository;
import cibertec.edu.repo.projection.ConteoView;
import cibertec.edu.repo.spec.EncuestaSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de negocio de encuestas.
 *
 * Las reglas (solo se edita/elimina en borrador, transiciones de estado que
 * solo avanzan) se validan aquí; los triggers de MySQL las respaldan como
 * última línea de defensa. Los listados usan Specifications (consultas
 * dinámicas) y proyecciones por interfaz para los conteos.
 */
@Service
@RequiredArgsConstructor
public class EncuestaService {

    /** Orden de los estados en el panel admin. */
    private static final List<String> ORDEN_ESTADOS =
            List.of("borrador", "activa", "finalizada", "cancelada");

    private final EncuestaRepository encuestaRepo;
    private final OpcionRepository   opcionRepo;
    private final VotoRepository     votoRepo;

    // ── Comandos (admin) ──────────────────────────────────────

    @Transactional
    public EncuestaDto crear(EncuestaRequest req, UUID adminId) {
        Encuesta encuesta = new Encuesta();
        encuesta.setTitulo(req.getTitulo());
        encuesta.setDescripcion(req.getDescripcion());
        encuesta.setCreadoPor(adminId);
        agregarOpciones(encuesta, req.getOpciones());

        encuestaRepo.saveAndFlush(encuesta);
        return mapDto(encuesta, null);
    }

    @Transactional
    public EncuestaDto actualizar(UUID id, EncuestaRequest req) {
        Encuesta encuesta = cargar(id);
        exigirBorrador(encuesta, "Solo se pueden editar encuestas en borrador");

        encuesta.setTitulo(req.getTitulo());
        encuesta.setDescripcion(req.getDescripcion());

        // Borra las opciones actuales y vacía antes de insertar las nuevas, para
        // no chocar con el UNIQUE (encuesta_id, orden) durante el reemplazo.
        encuesta.getOpciones().clear();
        encuestaRepo.saveAndFlush(encuesta);

        agregarOpciones(encuesta, req.getOpciones());
        encuestaRepo.saveAndFlush(encuesta);
        return mapDto(encuesta, null);
    }

    @Transactional
    public EncuestaDto cambiarEstado(UUID id, String nuevoEstadoStr) {
        Encuesta encuesta = cargar(id);
        EstadoEncuesta actual  = EstadoEncuesta.desde(encuesta.getEstado());
        EstadoEncuesta destino = parsearEstado(nuevoEstadoStr);

        if (actual == destino) {
            return mapDto(encuesta, null);   // sin cambios
        }
        if (!actual.puedeTransicionarA(destino)) {
            throw new EstadoInvalidoException(
                    "Transición no permitida: " + actual.valor() + " → " + destino.valor()
                            + ". Flujo válido: borrador → activa → finalizada;"
                            + " se puede cancelar desde borrador o activa.");
        }
        encuesta.setEstado(destino.valor());
        // El trigger sella las fechas (activada_en/finalizada_en/cancelada_en);
        // al ser @Generated, Hibernate las relee tras el UPDATE.
        encuestaRepo.saveAndFlush(encuesta);
        return mapDto(encuesta, null);
    }

    @Transactional
    public void borrar(UUID id) {
        Encuesta encuesta = cargar(id);
        exigirBorrador(encuesta, "Solo se pueden eliminar encuestas en borrador");
        encuestaRepo.delete(encuesta);
    }

    // ── Consultas ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EncuestaResumenDto> listarTodas() {
        List<Encuesta> encuestas = encuestaRepo.findAll();
        encuestas.sort(Comparator
                .comparingInt((Encuesta e) -> ORDEN_ESTADOS.indexOf(e.getEstado()))
                .thenComparing(Encuesta::getCreadoEn, Comparator.reverseOrder()));
        return mapResumen(encuestas, null);
    }

    @Transactional(readOnly = true)
    public List<EncuestaResumenDto> listarActivas(UUID usuarioId) {
        List<Encuesta> encuestas = encuestaRepo.findAll(
                EncuestaSpecifications.estado(EstadoEncuesta.ACTIVA.valor()),
                Sort.by(Sort.Direction.DESC, "creadoEn"));
        return mapResumen(encuestas, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<EncuestaResumenDto> listarFinalizadas(UsuarioPrincipal principal) {
        if (esAdmin(principal)) {
            List<Encuesta> encuestas = encuestaRepo.findAll(
                    EncuestaSpecifications.estado(EstadoEncuesta.FINALIZADA.valor()),
                    Sort.by(Sort.Direction.DESC, "finalizadaEn"));
            return mapResumen(encuestas, null);
        }

        Specification<Encuesta> spec = EncuestaSpecifications
                .estado(EstadoEncuesta.FINALIZADA.valor())
                .and(EncuestaSpecifications.participoUsuario(principal.id()));
        List<Encuesta> encuestas = encuestaRepo.findAll(
                spec, Sort.by(Sort.Direction.DESC, "finalizadaEn"));
        return mapResumen(encuestas, principal.id());
    }

    @Transactional(readOnly = true)
    public EncuestaDto obtenerDetalle(UUID id, UsuarioPrincipal principal) {
        Encuesta encuesta = cargar(id);

        // Un usuario no-admin no puede ver borradores: se oculta como inexistente.
        if (!esAdmin(principal) && EstadoEncuesta.BORRADOR.valor().equals(encuesta.getEstado())) {
            throw new RecursoNoEncontradoException("Encuesta no encontrada");
        }

        Boolean yaVoto = esAdmin(principal)
                ? null
                : votoRepo.existsByEncuestaIdAndUsuarioId(id, principal.id());
        return mapDto(encuesta, yaVoto);
    }

    // ── Internos ──────────────────────────────────────────────

    private Encuesta cargar(UUID id) {
        return encuestaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));
    }

    private static void agregarOpciones(Encuesta encuesta, List<String> opciones) {
        int orden = 0;
        for (String texto : opciones) {
            encuesta.agregarOpcion(new Opcion(texto.trim(), orden++));
        }
    }

    private static void exigirBorrador(Encuesta encuesta, String mensaje) {
        if (!EstadoEncuesta.BORRADOR.valor().equals(encuesta.getEstado())) {
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

    /**
     * Mapea una lista de encuestas a resúmenes, resolviendo los conteos
     * (opciones, votos y "ya votó") en consultas agregadas para evitar N+1.
     * Si usuarioId es null (vistas de admin), yaVoto queda null.
     */
    private List<EncuestaResumenDto> mapResumen(List<Encuesta> encuestas, UUID usuarioId) {
        if (encuestas.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = encuestas.stream().map(Encuesta::getId).toList();

        Map<UUID, Long> totalOpciones = toMap(opcionRepo.contarPorEncuesta(ids));
        Map<UUID, Long> totalVotos    = toMap(votoRepo.contarPorEncuesta(ids));
        Set<UUID> votadas = (usuarioId == null)
                ? Set.of()
                : new HashSet<>(votoRepo.encuestasVotadasPor(usuarioId, ids));

        return encuestas.stream()
                .map(e -> new EncuestaResumenDto(
                        e.getId(),
                        e.getTitulo(),
                        e.getDescripcion(),
                        e.getEstado(),
                        e.getCreadoEn(),
                        totalOpciones.getOrDefault(e.getId(), 0L).intValue(),
                        totalVotos.getOrDefault(e.getId(), 0L),
                        usuarioId == null ? null : votadas.contains(e.getId())))
                .toList();
    }

    private static Map<UUID, Long> toMap(List<ConteoView> conteos) {
        return conteos.stream()
                .collect(Collectors.toMap(ConteoView::getId, ConteoView::getTotal));
    }

    private EncuestaDto mapDto(Encuesta e, Boolean yaVoto) {
        List<OpcionDto> opciones = e.getOpciones().stream()
                .map(o -> new OpcionDto(o.getId(), o.getTexto(), o.getOrden()))
                .toList();
        return new EncuestaDto(
                e.getId(), e.getTitulo(), e.getDescripcion(), e.getEstado(),
                e.getCreadoEn(), e.getActivadaEn(), e.getFinalizadaEn(), e.getCanceladaEn(),
                opciones, yaVoto);
    }
}
