package com.duoc.cursos.repository;

import com.duoc.cursos.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    /** >= y no >: el cliente guarda servidorEn y lo reenvía; una fila escrita en ese mismo instante no debe perderse. */
    List<Proyecto> findByActualizadoEnGreaterThanEqual(Instant desde);
}
