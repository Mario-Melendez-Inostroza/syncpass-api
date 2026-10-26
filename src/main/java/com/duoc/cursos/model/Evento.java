package com.duoc.cursos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Evento o taller de un proyecto (caso §3.2). */
@Entity
@Table(name = "eventos")
@Getter
@Setter
public class Evento {

    /** Radio de validación por defecto, en metros, cuando el evento no define uno. */
    public static final int RADIO_POR_DEFECTO_METROS = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "fecha_hora_programada", nullable = false)
    private Instant fechaHoraProgramada;

    @Column(name = "cupos_maximos")
    private Integer cuposMaximos;

    @Column(name = "recinto", length = 200)
    private String recinto;

    /** Coordenadas aproximadas del recinto. Si faltan, toda marcación queda SIN_UBICACION. */
    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    @Column(name = "radio_metros", nullable = false)
    private Integer radioMetros = RADIO_POR_DEFECTO_METROS;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        this.actualizadoEn = Instant.now();
    }
}
