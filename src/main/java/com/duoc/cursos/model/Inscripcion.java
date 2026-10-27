package com.duoc.cursos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Inscripción de un asistente a un taller, hecha por él mismo desde su cuenta (caso §3.1). */
@Entity
@Table(name = "inscripciones")
@Getter
@Setter
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asistente_id", nullable = false)
    private Asistente asistente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @PrePersist
    void marcarCreacion() {
        this.creadaEn = Instant.now();
    }
}
