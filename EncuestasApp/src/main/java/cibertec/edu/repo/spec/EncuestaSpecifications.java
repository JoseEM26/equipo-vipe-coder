package cibertec.edu.repo.spec;

import cibertec.edu.entity.Encuesta;
import cibertec.edu.entity.Voto;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Especificaciones JPA (Criteria API) para construir consultas dinámicas y
 * componibles sobre Encuesta. Se usan con JpaSpecificationExecutor.
 */
public final class EncuestaSpecifications {

    private EncuestaSpecifications() {}

    /** Filtra por estado exacto (borrador, activa, finalizada, cancelada). */
    public static Specification<Encuesta> estado(String estado) {
        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    /**
     * Filtra encuestas en las que el usuario indicado emitió un voto,
     * mediante una subconsulta EXISTS sobre la tabla de votos.
     */
    public static Specification<Encuesta> participoUsuario(UUID usuarioId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Voto> voto = sub.from(Voto.class);
            sub.select(cb.literal(1L));
            sub.where(
                    cb.equal(voto.get("encuestaId"), root.get("id")),
                    cb.equal(voto.get("usuarioId"), usuarioId));
            return cb.exists(sub);
        };
    }
}
