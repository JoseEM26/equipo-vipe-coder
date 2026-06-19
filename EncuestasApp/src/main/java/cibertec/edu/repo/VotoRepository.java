package cibertec.edu.repo;

import cibertec.edu.entity.Voto;
import cibertec.edu.repo.projection.ConteoView;
import cibertec.edu.repo.projection.ResultadoOpcionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repositorio Spring Data JPA para votos y resultados agregados. */
public interface VotoRepository extends JpaRepository<Voto, UUID> {

    /** ¿El usuario ya votó en esta encuesta? */
    boolean existsByEncuestaIdAndUsuarioId(UUID encuestaId, UUID usuarioId);

    /**
     * Resultados agregados de una encuesta leídos de la vista
     * v_resultados_encuesta, mapeados a una proyección por interfaz.
     * (encuestaId va como String porque la columna es CHAR(36).)
     */
    @Query(value = """
            SELECT opcion_id     AS opcionId,
                   opcion_texto  AS texto,
                   orden         AS orden,
                   total_votos   AS totalVotos,
                   porcentaje    AS porcentaje
            FROM   v_resultados_encuesta
            WHERE  encuesta_id = :encuestaId
            ORDER BY orden
            """, nativeQuery = true)
    List<ResultadoOpcionView> resultados(@Param("encuestaId") String encuestaId);

    /** Conteo de votos por encuesta (proyección por interfaz). */
    @Query("""
            SELECT v.encuestaId AS id, COUNT(v) AS total
            FROM   Voto v
            WHERE  v.encuestaId IN :ids
            GROUP BY v.encuestaId
            """)
    List<ConteoView> contarPorEncuesta(@Param("ids") Collection<UUID> ids);

    /** Ids de las encuestas (dentro del conjunto dado) en las que votó el usuario. */
    @Query("""
            SELECT DISTINCT v.encuestaId
            FROM   Voto v
            WHERE  v.usuarioId = :usuarioId AND v.encuestaId IN :ids
            """)
    List<UUID> encuestasVotadasPor(@Param("usuarioId") UUID usuarioId,
                                   @Param("ids") Collection<UUID> ids);
}
