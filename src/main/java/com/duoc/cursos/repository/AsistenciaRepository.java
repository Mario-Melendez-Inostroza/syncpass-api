package com.duoc.cursos.repository;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.enums.EstadoValidacion;
import com.duoc.cursos.sync.dto.AsistenciaFilaDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    Optional<Asistencia> findByUuid(String uuid);

    Optional<Asistencia> findByEventoIdAndAsistenteId(Long eventoId, Long asistenteId);

    // --- Resumen / quórum de un evento ---

    long countByEventoId(Long eventoId);

    boolean existsByEventoId(Long eventoId);

    long countByEventoIdAndEstadoValidacion(Long eventoId, EstadoValidacion estado);

    @Query("select min(a.marcadoEn) from Asistencia a where a.evento.id = :eventoId")
    Instant primeraMarcacion(@Param("eventoId") Long eventoId);

    @Query("select max(a.marcadoEn) from Asistencia a where a.evento.id = :eventoId")
    Instant ultimaMarcacion(@Param("eventoId") Long eventoId);

    // --- Reporte de asistencia de un evento (CSV) ---

    /**
     * Una fila por asistencia, con su encuesta si la hay. Proyecta solo lo que el reporte necesita: no incluye
     * quién registró (registradoPorSub), coordenadas del asistente ni comentarios (caso §5.1).
     */
    @Query("select new com.duoc.cursos.sync.dto.AsistenciaFilaDto(s.codigo, s.tipo, s.estadoInscripcion, "
            + "a.marcadoEn, a.estadoValidacion, a.distanciaMetros, e.satisfaccion, e.utilidad) "
            + "from Asistencia a join a.asistente s left join Encuesta e on e.asistencia = a "
            + "where a.evento.id = :eventoId order by a.marcadoEn, a.id")
    List<AsistenciaFilaDto> filasPorEvento(@Param("eventoId") Long eventoId);
}
