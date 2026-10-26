package com.duoc.cursos.model;

import com.duoc.cursos.model.enums.EstadoValidacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Marcación de asistencia de un asistente en un evento (caso §3.2).
 *
 * Dos restricciones únicas, con funciones distintas:
 *  - uuid: idempotencia. Lo genera el teléfono, así que reenviar el mismo lote no duplica filas.
 *  - (evento, asistente): una marcación por persona y evento.
 */
@Entity
@Table(name = "asistencias", uniqueConstraints = {
        @UniqueConstraint(name = "uk_asistencias_uuid", columnNames = "uuid"),
        @UniqueConstraint(name = "uk_asistencias_evento_asistente", columnNames = {"evento_id", "asistente_id"})
})
@Getter
@Setter
public class Asistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asistente_id", nullable = false)
    private Asistente asistente;

    /** Reloj del dispositivo: la hora real de la marcación en terreno, aunque se suba horas después. */
    @Column(name = "marcado_en", nullable = false)
    private Instant marcadoEn;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    /** Distancia al recinto, calculada por el servidor. Nula si no hubo coordenadas. */
    @Column(name = "distancia_metros")
    private Integer distanciaMetros;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false, length = 20)
    private EstadoValidacion estadoValidacion;

    /**
     * Claim "sub" del JWT de quien subió el registro. Columna plana, sin FK a usuarios: el móvil envía el
     * access token, que no trae email ni name, y Usuario los exige NOT NULL, así que no se puede crear la
     * fila del usuario al vuelo. Nunca viene del cuerpo de la petición.
     */
    @Column(name = "registrado_por_sub", nullable = false, length = 64)
    private String registradoPorSub;

    /** Hora del servidor al recibirlo; distinta de marcadoEn. */
    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;
}
