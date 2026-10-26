package com.duoc.cursos.repository;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.enums.EstadoValidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByUuid(String uuid);

    Optional<Asistencia> findByEventoIdAndAsistenteId(Long eventoId, Long asistenteId);

    // --- Resumen / quórum de un evento ---

    long countByEventoId(Long eventoId);

    long countByEventoIdAndEstadoValidacion(Long eventoId, EstadoValidacion estado);

    @Query("select min(a.marcadoEn) from Asistencia a where a.evento.id = :eventoId")
    Instant primeraMarcacion(@Param("eventoId") Long eventoId);

    @Query("select max(a.marcadoEn) from Asistencia a where a.evento.id = :eventoId")
    Instant ultimaMarcacion(@Param("eventoId") Long eventoId);
}
