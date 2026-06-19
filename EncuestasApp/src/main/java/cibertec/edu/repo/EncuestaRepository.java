package cibertec.edu.repo;

import cibertec.edu.entity.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Repositorio Spring Data JPA para encuestas.
 *
 * Extiende JpaSpecificationExecutor para soportar consultas dinámicas con
 * Specifications (ver EncuestaSpecifications).
 */
public interface EncuestaRepository
        extends JpaRepository<Encuesta, UUID>, JpaSpecificationExecutor<Encuesta> {
}
