package cibertec.edu.services;

import cibertec.edu.dto.response.ResultadoEncuestaDto;
import cibertec.edu.dto.response.ResultadoOpcionDto;
import cibertec.edu.entity.Encuesta;
import cibertec.edu.entity.EstadoEncuesta;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.entity.Voto;
import cibertec.edu.exception.AccesoDenegadoException;
import cibertec.edu.exception.ConflictoException;
import cibertec.edu.exception.EstadoInvalidoException;
import cibertec.edu.exception.RecursoNoEncontradoException;
import cibertec.edu.repo.EncuestaRepository;
import cibertec.edu.repo.OpcionRepository;
import cibertec.edu.repo.VotoRepository;
import cibertec.edu.repo.projection.ResultadoOpcionView;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lógica de votación y resultados en tiempo real.
 *
 * Tras cada voto, publica los resultados actualizados en
 * /topic/admin/encuesta/{id}, un canal en tiempo real reservado a
 * administradores (la autorización la aplica WebSocketAuthInterceptor).
 * Los usuarios normales obtienen los resultados por REST: en la respuesta
 * al votar, o vía GET /resultados tras haber votado / al finalizar.
 */
@Service
@RequiredArgsConstructor
public class VotoService {

    private static final String TOPIC_ADMIN_PREFIX = "/topic/admin/encuesta/";

    private final VotoRepository        votoRepo;
    private final EncuestaRepository    encuestaRepo;
    private final OpcionRepository      opcionRepo;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public ResultadoEncuestaDto votar(UUID encuestaId, UUID opcionId, UUID usuarioId) {
        Encuesta encuesta = encuestaRepo.findById(encuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));

        if (!EstadoEncuesta.ACTIVA.valor().equals(encuesta.getEstado())) {
            throw new EstadoInvalidoException("Solo se puede votar en encuestas activas");
        }
        if (!opcionRepo.existsByIdAndEncuesta_Id(opcionId, encuestaId)) {
            throw new RecursoNoEncontradoException("La opción indicada no pertenece a esta encuesta");
        }
        if (votoRepo.existsByEncuestaIdAndUsuarioId(encuestaId, usuarioId)) {
            throw new ConflictoException("Ya has votado en esta encuesta");
        }

        votoRepo.save(new Voto(encuestaId, usuarioId, opcionId));

        // El votante recibe su snapshot en la respuesta REST; los admins, en vivo.
        ResultadoEncuestaDto resultado = construirResultado(encuesta);
        messaging.convertAndSend(TOPIC_ADMIN_PREFIX + encuestaId, resultado);
        return resultado;
    }

    /**
     * Resultados por REST. Visibilidad:
     *   - admin: siempre,
     *   - encuesta finalizada: público (cualquier usuario autenticado),
     *   - en otro caso: solo si el usuario ya votó.
     */
    @Transactional(readOnly = true)
    public ResultadoEncuestaDto resultados(UUID encuestaId, UsuarioPrincipal principal) {
        Encuesta encuesta = encuestaRepo.findById(encuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));

        boolean admin      = "admin".equalsIgnoreCase(principal.rol());
        boolean finalizada = EstadoEncuesta.FINALIZADA.valor().equals(encuesta.getEstado());

        if (!admin && !finalizada
                && !votoRepo.existsByEncuestaIdAndUsuarioId(encuestaId, principal.id())) {
            throw new AccesoDenegadoException(
                    "Debes votar en esta encuesta para ver los resultados");
        }
        return construirResultado(encuesta);
    }

    /**
     * Resultados públicos para el canal SOAP (sin autenticación).
     * Solo se exponen cuando la encuesta ha finalizado, igual que la regla
     * de visibilidad pública de {@link #resultados}.
     */
    @Transactional(readOnly = true)
    public ResultadoEncuestaDto resultadosPublicos(UUID encuestaId) {
        Encuesta encuesta = encuestaRepo.findById(encuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));

        if (!EstadoEncuesta.FINALIZADA.valor().equals(encuesta.getEstado())) {
            throw new AccesoDenegadoException(
                    "Los resultados solo son públicos cuando la encuesta ha finalizado");
        }
        return construirResultado(encuesta);
    }

    private ResultadoEncuestaDto construirResultado(Encuesta encuesta) {
        List<ResultadoOpcionDto> opciones = votoRepo.resultados(encuesta.getId().toString())
                .stream()
                .map(VotoService::mapOpcion)
                .toList();
        long total = opciones.stream().mapToLong(ResultadoOpcionDto::totalVotos).sum();
        return new ResultadoEncuestaDto(
                encuesta.getId(), encuesta.getTitulo(), encuesta.getEstado(), total, opciones);
    }

    private static ResultadoOpcionDto mapOpcion(ResultadoOpcionView v) {
        return new ResultadoOpcionDto(
                UUID.fromString(v.getOpcionId()),
                v.getTexto(),
                v.getOrden(),
                v.getTotalVotos(),
                v.getPorcentaje() == null ? 0.0 : v.getPorcentaje());
    }
}
