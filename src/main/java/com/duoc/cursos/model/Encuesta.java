package com.duoc.cursos.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Encuesta de valoración (caso §3.2). Cuelga de la asistencia: solo valora quien asistió, y evento y
 * asistente se obtienen de ahí. Una encuesta por asistencia.
 */
@Entity
@Table(name = "encuestas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_encuestas_uuid", columnNames = "uuid"),
        @UniqueConstraint(name = "uk_encuestas_asistencia", columnNames = "asistencia_id")
})
@Getter
@Setter
public class Encuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asistencia_id", nullable = false)
    private Asistencia asistencia;

    @Min(1)
    @Max(5)
    @Column(name = "satisfaccion", nullable = false)
    private Integer satisfaccion;

    @Min(1)
    @Max(5)
    @Column(name = "utilidad", nullable = false)
    private Integer utilidad;

    @Column(name = "comentarios", length = 1000)
    private String comentarios;

    /** Reloj del dispositivo. */
    @Column(name = "enviada_en", nullable = false)
    private Instant enviadaEn;

    /** Reloj del servidor. */
    @Column(name = "recibida_en", nullable = false)
    private Instant recibidaEn;
}
