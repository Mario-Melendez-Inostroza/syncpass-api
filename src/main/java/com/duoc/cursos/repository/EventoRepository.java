package com.duoc.cursos.repository;

import com.duoc.cursos.model.Evento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    /** Carga el proyecto junto con el evento: el DTO expone proyectoId y se arma fuera de la sesión. */
    @EntityGraph(attributePaths = "proyecto")
    List<Evento> findByActualizadoEnGreaterThanEqual(Instant desde);

    @Override
    @EntityGraph(attributePaths = "proyecto")
    List<Evento> findAll();
}
