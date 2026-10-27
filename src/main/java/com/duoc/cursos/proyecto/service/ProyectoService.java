package com.duoc.cursos.proyecto.service;

import com.duoc.cursos.exception.ConflictoException;
import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.model.enums.EstadoProyecto;
import com.duoc.cursos.proyecto.dto.ProyectoRequest;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import com.duoc.cursos.sync.dto.ProyectoDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fichas de proyectos que gestiona el Docente / Coordinador(a) (caso §3.1). */
@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;

    public ProyectoService(ProyectoRepository proyectoRepository, EventoRepository eventoRepository) {
        this.proyectoRepository = proyectoRepository;
        this.eventoRepository = eventoRepository;
    }

    @Transactional
    public ProyectoDto crear(ProyectoRequest req) {
        Proyecto proyecto = new Proyecto();
        aplicar(proyecto, req);
        return ProyectoDto.de(proyectoRepository.saveAndFlush(proyecto));
    }

    @Transactional
    public ProyectoDto actualizar(Long id, ProyectoRequest req) {
        Proyecto proyecto = buscar(id);
        aplicar(proyecto, req);
        return ProyectoDto.de(proyectoRepository.saveAndFlush(proyecto));
    }

    /** Un proyecto con eventos no se elimina: habría que decidir qué pasa con sus asistencias. */
    @Transactional
    public void eliminar(Long id) {
        Proyecto proyecto = buscar(id);
        if (eventoRepository.existsByProyectoId(id)) {
            throw new ConflictoException("El proyecto tiene eventos; elimínalos primero");
        }
        proyectoRepository.delete(proyecto);
    }

    private Proyecto buscar(Long id) {
        return proyectoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proyecto no existe"));
    }

    private static void aplicar(Proyecto p, ProyectoRequest req) {
        p.setTitulo(req.getTitulo().trim());
        p.setDescripcion(req.getDescripcion());
        p.setAreaTematica(req.getAreaTematica().trim());
        p.setUnidadVecinal(req.getUnidadVecinal().trim());
        p.setFechaInicio(req.getFechaInicio());
        p.setFechaTermino(req.getFechaTermino());
        p.setEstado(req.getEstado() != null ? req.getEstado() : EstadoProyecto.PLANIFICADO);
    }
}
