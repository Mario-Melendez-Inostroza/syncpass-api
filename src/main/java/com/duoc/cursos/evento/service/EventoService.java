package com.duoc.cursos.evento.service;

import com.duoc.cursos.evento.dto.EventoRequest;
import com.duoc.cursos.exception.ConflictoException;
import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import com.duoc.cursos.sync.dto.AsistenciaFilaDto;
import com.duoc.cursos.sync.dto.ComentarioDto;
import com.duoc.cursos.sync.dto.EventoDto;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Eventos o talleres que gestiona el Docente / Coordinador(a) (caso §3.1) y su reporte de asistencia. */
@Service
public class EventoService {

    /** Tope de comentarios que devuelve un evento: los más recientes. */
    static final int MAX_COMENTARIOS = 200;

    private final EventoRepository eventoRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;

    public EventoService(EventoRepository eventoRepository,
                         ProyectoRepository proyectoRepository,
                         AsistenciaRepository asistenciaRepository,
                         EncuestaRepository encuestaRepository) {
        this.eventoRepository = eventoRepository;
        this.proyectoRepository = proyectoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
    }

    @Transactional
    public EventoDto crear(EventoRequest req) {
        Evento evento = new Evento();
        aplicar(evento, req);
        return EventoDto.de(eventoRepository.saveAndFlush(evento));
    }

    @Transactional
    public EventoDto actualizar(Long id, EventoRequest req) {
        Evento evento = buscar(id);
        aplicar(evento, req);
        return EventoDto.de(eventoRepository.saveAndFlush(evento));
    }

    /**
     * Un evento con asistencias no se elimina: sería perder evidencia de terreno (caso §4.1, cero pérdida de
     * información). Además el catálogo incremental no propaga borrados a los teléfonos ya sincronizados.
     */
    @Transactional
    public void eliminar(Long id) {
        Evento evento = buscar(id);
        if (asistenciaRepository.existsByEventoId(id)) {
            throw new ConflictoException("El evento ya tiene asistencias registradas y no se puede eliminar");
        }
        eventoRepository.delete(evento);
    }

    @Transactional(readOnly = true)
    public List<AsistenciaFilaDto> asistencias(Long eventoId) {
        buscar(eventoId);
        return asistenciaRepository.filasPorEvento(eventoId);
    }

    /** Los comentarios de las encuestas del evento (los más recientes primero), sin nada que identifique al asistente. */
    @Transactional(readOnly = true)
    public List<ComentarioDto> comentarios(Long eventoId) {
        buscar(eventoId);
        return encuestaRepository.comentariosPorEvento(eventoId, Pageable.ofSize(MAX_COMENTARIOS));
    }

    private Evento buscar(Long id) {
        return eventoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento no existe"));
    }

    private void aplicar(Evento e, EventoRequest req) {
        Proyecto proyecto = proyectoRepository.findById(req.getProyectoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El proyecto no existe"));
        e.setProyecto(proyecto);
        e.setNombre(req.getNombre().trim());
        e.setFechaHoraProgramada(req.getFechaHoraProgramada());
        e.setCuposMaximos(req.getCuposMaximos());
        e.setQuorumMinimo(req.getQuorumMinimo());
        e.setRecinto(req.getRecinto());
        e.setLatitud(req.getLatitud());
        e.setLongitud(req.getLongitud());
        e.setRadioMetros(req.getRadioMetros() != null ? req.getRadioMetros() : Evento.RADIO_POR_DEFECTO_METROS);
    }
}
