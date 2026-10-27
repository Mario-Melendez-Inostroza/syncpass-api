package com.duoc.cursos.repository;

import com.duoc.cursos.model.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    boolean existsByAsistenteIdAndEventoId(Long asistenteId, Long eventoId);

    Optional<Inscripcion> findByAsistenteIdAndEventoId(Long asistenteId, Long eventoId);

    long countByEventoId(Long eventoId);

    /** Pares [eventoId, cantidad] de inscritos, para mostrar los cupos de todos los talleres sin una consulta por evento. */
    @Query("select i.evento.id, count(i) from Inscripcion i group by i.evento.id")
    List<Object[]> contarPorEvento();

    @Query("select i.evento.id from Inscripcion i where i.asistente.id = :asistenteId")
    List<Long> eventosDe(@Param("asistenteId") Long asistenteId);
}
