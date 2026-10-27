package com.duoc.cursos.repository;

import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.asistente.dto.AsistenteFilaDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsistenteRepository extends JpaRepository<Asistente, Long> {

    List<Asistente> findByActualizadoEnGreaterThanEqual(Instant desde);

    Optional<Asistente> findByUuid(String uuid);

    /** El asistente de una cuenta (grupo ASISTENTE), por el sub de Cognito. */
    Optional<Asistente> findByUsuarioSub(String usuarioSub);

    /** La lista del coordinador: cada asistente con en cuántos eventos fue marcado. */
    @Query("select new com.duoc.cursos.asistente.dto.AsistenteFilaDto(a.id, a.codigo, a.tipo, a.estadoInscripcion, count(s.id), "
            + "case when a.uuid is null then false else true end) "
            + "from Asistente a left join Asistencia s on s.asistente = a "
            + "group by a.id, a.codigo, a.tipo, a.estadoInscripcion, a.uuid order by a.codigo")
    List<AsistenteFilaDto> listarConAsistencias();
}
