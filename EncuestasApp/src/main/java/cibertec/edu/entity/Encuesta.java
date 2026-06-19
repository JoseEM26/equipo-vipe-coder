package cibertec.edu.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad JPA de la tabla 'encuestas'.
 *
 * Las transiciones de estado y el sellado de fechas (activada_en, etc.) los
 * realiza el trigger de MySQL como última línea de defensa; por eso esas
 * columnas van con @Generated (Hibernate las relee tras cada UPDATE).
 */
@Entity
@Table(name = "encuestas")
@Getter
@Setter
@NoArgsConstructor
public class Encuesta {

    @Id
    @UuidGenerator
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36, updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    /** ENUM('borrador','activa','finalizada','cancelada'); mapeado como String. */
    @Column(nullable = false)
    private String estado = "borrador";

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "creado_por", nullable = false, length = 36)
    private UUID creadoPor;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "actualizado_en", insertable = false, updatable = false)
    private LocalDateTime actualizadoEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "activada_en", insertable = false, updatable = false)
    private LocalDateTime activadaEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "finalizada_en", insertable = false, updatable = false)
    private LocalDateTime finalizadaEn;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "cancelada_en", insertable = false, updatable = false)
    private LocalDateTime canceladaEn;

    @OneToMany(mappedBy = "encuesta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<Opcion> opciones = new ArrayList<>();

    /** Agrega una opción manteniendo la relación bidireccional. */
    public void agregarOpcion(Opcion opcion) {
        opcion.setEncuesta(this);
        this.opciones.add(opcion);
    }
}
