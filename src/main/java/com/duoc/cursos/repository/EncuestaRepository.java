package com.duoc.cursos.repository;

import com.duoc.cursos.model.Encuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EncuestaRepository extends JpaRepository<Encuesta, Long> {

    Optional<Encuesta> findByUuid(String uuid);

    Optional<Encuesta> findByAsistenciaId(Long asistenciaId);

    // --- Resumen de un evento (null cuando no hay encuestas) ---

    @Query("select count(e) from Encuesta e where e.asistencia.evento.id = :eventoId")
    long contarPorEvento(@Param("eventoId") Long eventoId);

    @Query("select avg(e.satisfaccion) from Encuesta e where e.asistencia.evento.id = :eventoId")
    Double promedioSatisfaccion(@Param("eventoId") Long eventoId);

    @Query("select avg(e.utilidad) from Encuesta e where e.asistencia.evento.id = :eventoId")
    Double promedioUtilidad(@Param("eventoId") Long eventoId);
}
