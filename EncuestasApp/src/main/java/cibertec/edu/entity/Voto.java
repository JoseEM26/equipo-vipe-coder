package cibertec.edu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidad JPA de la tabla 'votos'.
 *
 * Las claves foráneas se mapean como UUID planos (no relaciones) porque la
 * lógica solo necesita los identificadores; la validación de "encuesta activa"
 * y "opción perteneciente" la garantizan el service y el trigger de MySQL.
 */
@Entity
@Table(name = "votos")
@Getter
@Setter
@NoArgsConstructor
public class Voto {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "encuesta_id", nullable = false, length = 36)
    private UUID encuestaId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "usuario_id", nullable = false, length = 36)
    private UUID usuarioId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "opcion_id", nullable = false, length = 36)
    private UUID opcionId;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "votado_en", insertable = false, updatable = false)
    private LocalDateTime votadoEn;

    public Voto(UUID encuestaId, UUID usuarioId, UUID opcionId) {
        this.encuestaId = encuestaId;
        this.usuarioId = usuarioId;
        this.opcionId = opcionId;
    }
}
