package cibertec.edu.auth;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a datos para autenticación.
 * Usa NamedParameterJdbcTemplate (más legible que JdbcTemplate con índices).
 */
@Repository
public class AuthRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AuthRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Busca un usuario activo por email.
     * Devuelve Optional.empty() si no existe o está inactivo.
     */
    public Optional<UsuarioAuth> findByEmail(String email) {
        String sql = """
                SELECT id, nombre, email, password_hash, rol
                FROM   usuarios
                WHERE  email  = :email
                AND    activo = TRUE
                """;

        var params = new MapSqlParameterSource("email", email);

        return jdbc.query(sql, params, (rs, rowNum) -> new UsuarioAuth(
                UUID.fromString(rs.getString("id")),
                rs.getString("nombre"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("rol")
        )).stream().findFirst();
    }

    /**
     * Verifica si ya existe un usuario con ese email.
     */
    public boolean existeEmail(String email) {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE email = :email";
        var params = new MapSqlParameterSource("email", email);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    /**
     * Inserta un nuevo usuario con rol 'usuario' por defecto.
     * El rol solo puede ser 'admin' si se asigna manualmente en la BD.
     */
    public UUID registrar(String nombre, String email, String passwordHash) {
        String sql = """
                INSERT INTO usuarios (nombre, email, password_hash, rol)
                VALUES (:nombre, :email, :passwordHash, 'usuario')
                RETURNING id
                """;

        var params = new MapSqlParameterSource()
                .addValue("nombre",       nombre)
                .addValue("email",        email)
                .addValue("passwordHash", passwordHash);

        return jdbc.queryForObject(sql, params, (rs, rowNum) ->
                UUID.fromString(rs.getString("id")));
    }

    // ── Proyección interna ────────────────────────────────────

    /**
     * Proyección con los campos mínimos necesarios para autenticar.
     * No es una entidad completa, no sale de la capa auth.
     */
    public record UsuarioAuth(
            UUID   id,
            String nombre,
            String email,
            String passwordHash,
            String rol
    ) {}
}
