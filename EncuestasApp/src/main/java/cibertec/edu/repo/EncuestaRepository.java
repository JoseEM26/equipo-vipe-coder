package cibertec.edu.repo;

import cibertec.edu.dto.response.EncuestaResumenDto;
import cibertec.edu.dto.response.OpcionDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos de encuestas y sus opciones.
 *
 * Las reglas de negocio (transiciones de estado, bloqueo de edición fuera
 * de borrador) se validan en EncuestaService; los triggers de MySQL actúan
 * como última línea de defensa.
 */
@Repository
public class EncuestaRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EncuestaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Escritura ─────────────────────────────────────────────

    /** Inserta una encuesta en estado 'borrador' y devuelve su id. */
    public UUID crear(String titulo, String descripcion, UUID creadoPor) {
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO encuestas (id, titulo, descripcion, creado_por)
                VALUES (:id, :titulo, :descripcion, :creadoPor)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id",          id.toString())
                .addValue("titulo",      titulo)
                .addValue("descripcion", descripcion)
                .addValue("creadoPor",   creadoPor.toString()));
        return id;
    }

    public void crearOpcion(UUID encuestaId, String texto, int orden) {
        String sql = """
                INSERT INTO opciones (id, encuesta_id, texto, orden)
                VALUES (:id, :encuestaId, :texto, :orden)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id",         UUID.randomUUID().toString())
                .addValue("encuestaId", encuestaId.toString())
                .addValue("texto",      texto)
                .addValue("orden",      orden));
    }

    /** Actualiza título/descripción. El trigger bloquea si no está en borrador. */
    public void actualizarDatos(UUID id, String titulo, String descripcion) {
        String sql = """
                UPDATE encuestas
                SET    titulo = :titulo, descripcion = :descripcion
                WHERE  id = :id
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id",          id.toString())
                .addValue("titulo",      titulo)
                .addValue("descripcion", descripcion));
    }

    public void borrarOpciones(UUID encuestaId) {
        jdbc.update("DELETE FROM opciones WHERE encuesta_id = :encuestaId",
                new MapSqlParameterSource("encuestaId", encuestaId.toString()));
    }

    /** Cambia el estado. El trigger valida la transición y sella las fechas. */
    public void cambiarEstado(UUID id, String nuevoEstado) {
        jdbc.update("UPDATE encuestas SET estado = :estado WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id",     id.toString())
                        .addValue("estado", nuevoEstado));
    }

    public void borrar(UUID id) {
        jdbc.update("DELETE FROM encuestas WHERE id = :id",
                new MapSqlParameterSource("id", id.toString()));
    }

    // ── Lectura ───────────────────────────────────────────────

    public Optional<EncuestaRow> buscarPorId(UUID id) {
        String sql = """
                SELECT id, titulo, descripcion, estado, creado_por,
                       creado_en, activada_en, finalizada_en, cancelada_en
                FROM   encuestas
                WHERE  id = :id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("id", id.toString()), ENCUESTA_ROW)
                .stream().findFirst();
    }

    public List<OpcionDto> opcionesDe(UUID encuestaId) {
        String sql = """
                SELECT id, texto, orden
                FROM   opciones
                WHERE  encuesta_id = :encuestaId
                ORDER BY orden
                """;
        return jdbc.query(sql, new MapSqlParameterSource("encuestaId", encuestaId.toString()),
                (rs, n) -> new OpcionDto(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("texto"),
                        rs.getInt("orden")));
    }

    /** Verifica que la opción exista y pertenezca a la encuesta indicada. */
    public boolean existeOpcionEnEncuesta(UUID opcionId, UUID encuestaId) {
        String sql = """
                SELECT EXISTS(
                    SELECT 1 FROM opciones
                    WHERE id = :opcionId AND encuesta_id = :encuestaId
                )
                """;
        Boolean r = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("opcionId",   opcionId.toString())
                .addValue("encuestaId", encuestaId.toString()), Boolean.class);
        return Boolean.TRUE.equals(r);
    }

    // ── Listados ──────────────────────────────────────────────

    /** Panel admin: todas las encuestas, ordenadas por estado y fecha. */
    public List<EncuestaResumenDto> listarTodas() {
        String sql = """
                SELECT e.id, e.titulo, e.descripcion, e.estado, e.creado_en,
                       (SELECT COUNT(*) FROM opciones o WHERE o.encuesta_id = e.id) AS total_opciones,
                       (SELECT COUNT(*) FROM votos    v WHERE v.encuesta_id = e.id) AS total_votos,
                       NULL AS ya_voto
                FROM   encuestas e
                ORDER BY FIELD(e.estado, 'borrador', 'activa', 'finalizada', 'cancelada'), e.creado_en DESC
                """;
        return jdbc.query(sql, RESUMEN_ROW);
    }

    /** Pantalla inicial del usuario: encuestas activas con marca de "ya votó". */
    public List<EncuestaResumenDto> listarActivas(UUID usuarioId) {
        String sql = """
                SELECT e.id, e.titulo, e.descripcion, e.estado, e.creado_en,
                       (SELECT COUNT(*) FROM opciones o WHERE o.encuesta_id = e.id) AS total_opciones,
                       (SELECT COUNT(*) FROM votos    v WHERE v.encuesta_id = e.id) AS total_votos,
                       EXISTS(SELECT 1 FROM votos vv
                              WHERE vv.encuesta_id = e.id AND vv.usuario_id = :usuarioId) AS ya_voto
                FROM   encuestas e
                WHERE  e.estado = 'activa'
                ORDER BY e.creado_en DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("usuarioId", usuarioId.toString()), RESUMEN_ROW);
    }

    /** Encuestas finalizadas (vista admin: todas). */
    public List<EncuestaResumenDto> listarFinalizadasTodas() {
        String sql = """
                SELECT e.id, e.titulo, e.descripcion, e.estado, e.creado_en,
                       (SELECT COUNT(*) FROM opciones o WHERE o.encuesta_id = e.id) AS total_opciones,
                       (SELECT COUNT(*) FROM votos    v WHERE v.encuesta_id = e.id) AS total_votos,
                       NULL AS ya_voto
                FROM   encuestas e
                WHERE  e.estado = 'finalizada'
                ORDER BY e.finalizada_en DESC
                """;
        return jdbc.query(sql, RESUMEN_ROW);
    }

    /** Encuestas finalizadas en las que participó el usuario (vista usuario). */
    public List<EncuestaResumenDto> listarFinalizadasDeUsuario(UUID usuarioId) {
        String sql = """
                SELECT e.id, e.titulo, e.descripcion, e.estado, e.creado_en,
                       (SELECT COUNT(*) FROM opciones o WHERE o.encuesta_id = e.id) AS total_opciones,
                       (SELECT COUNT(*) FROM votos    v WHERE v.encuesta_id = e.id) AS total_votos,
                       TRUE AS ya_voto
                FROM   encuestas e
                WHERE  e.estado = 'finalizada'
                AND    EXISTS(SELECT 1 FROM votos vv
                              WHERE vv.encuesta_id = e.id AND vv.usuario_id = :usuarioId)
                ORDER BY e.finalizada_en DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("usuarioId", usuarioId.toString()), RESUMEN_ROW);
    }

    // ── Mappers y proyección interna ──────────────────────────

    private static final RowMapper<EncuestaResumenDto> RESUMEN_ROW = (rs, n) -> {
        Object yv = rs.getObject("ya_voto");
        Boolean yaVoto = (yv == null) ? null : rs.getBoolean("ya_voto");
        return new EncuestaResumenDto(
                UUID.fromString(rs.getString("id")),
                rs.getString("titulo"),
                rs.getString("descripcion"),
                rs.getString("estado"),
                toLdt(rs.getTimestamp("creado_en")),
                rs.getInt("total_opciones"),
                rs.getLong("total_votos"),
                yaVoto);
    };

    private static final RowMapper<EncuestaRow> ENCUESTA_ROW = (rs, n) -> new EncuestaRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("titulo"),
            rs.getString("descripcion"),
            rs.getString("estado"),
            UUID.fromString(rs.getString("creado_por")),
            toLdt(rs.getTimestamp("creado_en")),
            toLdt(rs.getTimestamp("activada_en")),
            toLdt(rs.getTimestamp("finalizada_en")),
            toLdt(rs.getTimestamp("cancelada_en")));

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Proyección interna de una fila de encuesta (no sale de la capa de servicio). */
    public record EncuestaRow(
            UUID          id,
            String        titulo,
            String        descripcion,
            String        estado,
            UUID          creadoPor,
            LocalDateTime creadoEn,
            LocalDateTime activadaEn,
            LocalDateTime finalizadaEn,
            LocalDateTime canceladaEn
    ) {}
}
