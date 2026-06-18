package cibertec.edu.services;

import cibertec.edu.dto.response.ResultadoEncuestaDto;
import cibertec.edu.dto.response.ResultadoOpcionDto;
import cibertec.edu.entity.EstadoEncuesta;
import cibertec.edu.entity.UsuarioPrincipal;
import cibertec.edu.exception.AccesoDenegadoException;
import cibertec.edu.exception.ConflictoException;
import cibertec.edu.exception.EstadoInvalidoException;
import cibertec.edu.exception.RecursoNoEncontradoException;
import cibertec.edu.repo.EncuestaRepository;
import cibertec.edu.repo.EncuestaRepository.EncuestaRow;
import cibertec.edu.repo.VotoRepository;
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
    private final SimpMessagingTemplate messaging;

    @Transactional
    public ResultadoEncuestaDto votar(UUID encuestaId, UUID opcionId, UUID usuarioId) {
        EncuestaRow encuesta = encuestaRepo.buscarPorId(encuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));

        if (!EstadoEncuesta.ACTIVA.valor().equals(encuesta.estado())) {
            throw new EstadoInvalidoException("Solo se puede votar en encuestas activas");
        }
        if (!encuestaRepo.existeOpcionEnEncuesta(opcionId, encuestaId)) {
            throw new RecursoNoEncontradoException("La opción indicada no pertenece a esta encuesta");
        }
        if (votoRepo.yaVoto(encuestaId, usuarioId)) {
            throw new ConflictoException("Ya has votado en esta encuesta");
        }

        votoRepo.registrar(encuestaId, usuarioId, opcionId);

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
        EncuestaRow encuesta = encuestaRepo.buscarPorId(encuestaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Encuesta no encontrada"));

        boolean admin      = "admin".equalsIgnoreCase(principal.rol());
        boolean finalizada = EstadoEncuesta.FINALIZADA.valor().equals(encuesta.estado());

        if (!admin && !finalizada && !votoRepo.yaVoto(encuestaId, principal.id())) {
            throw new AccesoDenegadoException(
                    "Debes votar en esta encuesta para ver los resultados");
        }
        return construirResultado(encuesta);
    }

    private ResultadoEncuestaDto construirResultado(EncuestaRow encuesta) {
        List<ResultadoOpcionDto> opciones = votoRepo.resultados(encuesta.id());
        long total = opciones.stream().mapToLong(ResultadoOpcionDto::totalVotos).sum();
        return new ResultadoEncuestaDto(
                encuesta.id(), encuesta.titulo(), encuesta.estado(), total, opciones);
    }
}
