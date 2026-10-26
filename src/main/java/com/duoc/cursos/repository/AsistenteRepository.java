package com.duoc.cursos.repository;

import com.duoc.cursos.model.Asistente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AsistenteRepository extends JpaRepository<Asistente, Long> {

    List<Asistente> findByActualizadoEnGreaterThanEqual(Instant desde);
}
