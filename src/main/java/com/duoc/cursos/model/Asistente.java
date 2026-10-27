package com.duoc.cursos.model;

import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Participante de un evento (caso §3.2).
 *
 * Deliberadamente SIN nombre, email, teléfono ni documento: el caso exige un "identificador
 * sintético/ficticio" y prohíbe datos personales reales (§5.1). Al no existir las columnas, no se puede
 * guardar un dato personal ni por error.
 */
@Entity
@Table(name = "asistentes")
@Getter
@Setter
public class Asistente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador sintético, p. ej. "ASI-0001". */
    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    /**
     * Identificador generado por el teléfono para un asistente dado de alta en terreno. NULL en los que carga
     * el servidor. Es la clave de idempotencia del alta (V3).
     */
    @Column(name = "uuid", length = 36, unique = true)
    private String uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoParticipante tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_inscripcion", nullable = false, length = 20)
    private EstadoInscripcion estadoInscripcion;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        this.actualizadoEn = Instant.now();
    }
}
