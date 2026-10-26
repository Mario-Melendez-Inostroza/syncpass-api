package com.duoc.cursos.model;

import com.duoc.cursos.model.enums.EstadoProyecto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Proyecto comunitario (caso §3.2). */
@Entity
@Table(name = "proyectos")
@Getter
@Setter
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", length = 1000)
    private String descripcion;

    @Column(name = "area_tematica", nullable = false, length = 80)
    private String areaTematica;

    @Column(name = "unidad_vecinal", nullable = false, length = 120)
    private String unidadVecinal;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_termino")
    private LocalDate fechaTermino;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoProyecto estado;

    /** Hora del servidor de la última modificación. Es lo que recorre GET /api/sync/catalogo?desde=. */
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        this.actualizadoEn = Instant.now();
    }
}
