package cibertec.edu.repo;

import cibertec.edu.entity.Opcion;
import cibertec.edu.repo.projection.ConteoView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Repositorio Spring Data JPA para opciones. */
public interface OpcionRepository extends JpaRepository<Opcion, UUID> {

    /** Verifica que la opción exista y pertenezca a la encuesta indicada. */
    boolean existsByIdAndEncuesta_Id(UUID id, UUID encuestaId);

    /** Conteo de opciones por encuesta (proyección por interfaz). */
    @Query("""
            SELECT o.encuesta.id AS id, COUNT(o) AS total
            FROM   Opcion o
            WHERE  o.encuesta.id IN :ids
            GROUP BY o.encuesta.id
            """)
    List<ConteoView> contarPorEncuesta(@Param("ids") Collection<UUID> ids);
}
