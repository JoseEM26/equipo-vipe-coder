package cibertec.edu.repo;

import cibertec.edu.dto.response.ResultadoOpcionDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Acceso a datos de votos y resultados agregados.
 * Los resultados se leen de la vista v_resultados_encuesta.
 */
@Repository
public class VotoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public VotoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ¿El usuario ya votó en esta encuesta? */
    public boolean yaVoto(UUID encuestaId, UUID usuarioId) {
        String sql = """
                SELECT EXISTS(
                    SELECT 1 FROM votos
                    WHERE encuesta_id = :encuestaId AND usuario_id = :usuarioId
                )
                """;
        Boolean r = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("encuestaId", encuestaId.toString())
                .addValue("usuarioId",  usuarioId.toString()), Boolean.class);
        return Boolean.TRUE.equals(r);
    }

    /**
     * Registra un voto. La unicidad (un voto por usuario/encuesta) la garantiza
     * el constraint uq_votos_usuario_encuesta; el trigger valida encuesta activa
     * y opción perteneciente como última línea de defensa.
     */
    public void registrar(UUID encuestaId, UUID usuarioId, UUID opcionId) {
        String sql = """
                INSERT INTO votos (id, encuesta_id, usuario_id, opcion_id)
                VALUES (:id, :encuestaId, :usuarioId, :opcionId)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id",         UUID.randomUUID().toString())
                .addValue("encuestaId", encuestaId.toString())
                .addValue("usuarioId",  usuarioId.toString())
                .addValue("opcionId",   opcionId.toString()));
    }

    /** Resultados agregados (todas las opciones, incluso con 0 votos). */
    public List<ResultadoOpcionDto> resultados(UUID encuestaId) {
        String sql = """
                SELECT opcion_id, opcion_texto, orden, total_votos, porcentaje
                FROM   v_resultados_encuesta
                WHERE  encuesta_id = :encuestaId
                ORDER BY orden
                """;
        return jdbc.query(sql, new MapSqlParameterSource("encuestaId", encuestaId.toString()),
                (rs, n) -> {
                    BigDecimal pct = rs.getBigDecimal("porcentaje");
                    return new ResultadoOpcionDto(
                            UUID.fromString(rs.getString("opcion_id")),
                            rs.getString("opcion_texto"),
                            rs.getInt("orden"),
                            rs.getLong("total_votos"),
                            pct == null ? 0.0 : pct.doubleValue());
                });
    }
}
