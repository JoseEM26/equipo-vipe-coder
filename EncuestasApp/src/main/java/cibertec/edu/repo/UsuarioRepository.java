package cibertec.edu.repo;

import cibertec.edu.entity.Usuario;
import cibertec.edu.repo.projection.UsuarioAuthView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositorio Spring Data JPA para usuarios. */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * Busca un usuario activo por email devolviendo solo los campos de
     * autenticación (proyección por interfaz, sin cargar la entidad completa).
     */
    Optional<UsuarioAuthView> findByEmailAndActivoTrue(String email);

    boolean existsByEmail(String email);
}
