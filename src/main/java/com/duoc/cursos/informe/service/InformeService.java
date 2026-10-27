package com.duoc.cursos.informe.service;

import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.informe.dto.InformeDtos.EventoInforme;
import com.duoc.cursos.informe.dto.InformeDtos.InformeResponse;
import com.duoc.cursos.informe.dto.InformeDtos.ProyectoInforme;
import com.duoc.cursos.informe.dto.InformeDtos.Totales;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import com.duoc.cursos.sync.dto.ComentarioDto;
import com.duoc.cursos.sync.dto.ProyectoDto;
import com.duoc.cursos.sync.dto.ResumenEventoResponse;
import com.duoc.cursos.sync.service.SyncService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Informe de impacto consolidado por proyecto y general (caso §2.2 punto 4 y §2.4: "automatizando la generación de
 * reportes e informes de impacto institucionales"). Se calcula al pedirlo, con lo que los teléfonos ya sincronizaron.
 */
@Service
public class InformeService {

    /** Cuántos comentarios por evento entran al informe (los más recientes). */
    static final int MAX_COMENTARIOS_POR_EVENTO = 100;

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final SyncService syncService;

    public InformeService(ProyectoRepository proyectoRepository,
                          EventoRepository eventoRepository,
                          AsistenciaRepository asistenciaRepository,
                          EncuestaRepository encuestaRepository,
                          SyncService syncService) {
        this.proyectoRepository = proyectoRepository;
        this.eventoRepository = eventoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
        this.syncService = syncService;
    }

    /** @param proyectoId un proyecto concreto, o null para todos. */
    @Transactional(readOnly = true)
    public InformeResponse generar(Long proyectoId) {
        List<Proyecto> proyectos;
        if (proyectoId == null) {
            proyectos = proyectoRepository.findAll().stream()
                    .sorted(Comparator.comparing(Proyecto::getTitulo, String.CASE_INSENSITIVE_ORDER)).toList();
        } else {
            proyectos = List.of(proyectoRepository.findById(proyectoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("El proyecto no existe")));
        }

        List<ProyectoInforme> informes = proyectos.stream().map(this::deProyecto).toList();
        List<EventoInforme> todosLosEventos = informes.stream().flatMap(p -> p.eventos().stream()).toList();
        // Una persona puede asistir a eventos de proyectos distintos: con varios proyectos hay que contarla una sola vez.
        long unicos = informes.size() == 1
                ? informes.get(0).totales().asistentesUnicos()
                : (proyectoId == null ? asistenciaRepository.asistentesUnicosGeneral() : 0);
        return new InformeResponse(Instant.now(), informes, totalizar(todosLosEventos, unicos));
    }

    private ProyectoInforme deProyecto(Proyecto p) {
        List<Evento> eventos = eventoRepository.findByProyectoIdOrderByFechaHoraProgramadaAsc(p.getId());
        List<EventoInforme> filas = eventos.stream().map(this::deEvento).toList();
        long unicos = asistenciaRepository.asistentesUnicosDeProyecto(p.getId());
        return new ProyectoInforme(ProyectoDto.de(p), filas, totalizar(filas, unicos));
    }

    private EventoInforme deEvento(Evento e) {
        ResumenEventoResponse r = syncService.resumen(e.getId());
        List<ComentarioDto> comentarios =
                encuestaRepository.comentariosPorEvento(e.getId(), Pageable.ofSize(MAX_COMENTARIOS_POR_EVENTO));
        return new EventoInforme(e.getId(), e.getNombre(), e.getFechaHoraProgramada(), e.getCuposMaximos(),
                r.quorumMinimo(), r.quorumCumplido(), r.totalMarcados(), r.validas(), r.fueraDeRadio(), r.sinUbicacion(),
                r.encuestasRecibidas(), tasa(r.encuestasRecibidas(), r.totalMarcados()),
                r.satisfaccionPromedio(), r.utilidadPromedio(), comentarios);
    }

    private static Totales totalizar(List<EventoInforme> eventos, long asistentesUnicos) {
        long marcados = eventos.stream().mapToLong(EventoInforme::totalMarcados).sum();
        long encuestas = eventos.stream().mapToLong(EventoInforme::encuestasRecibidas).sum();
        int conMinimo = (int) eventos.stream().filter(e -> e.quorumMinimo() != null).count();
        int cumplidos = (int) eventos.stream().filter(e -> Boolean.TRUE.equals(e.quorumCumplido())).count();
        return new Totales(eventos.size(), conMinimo, cumplidos, marcados, asistentesUnicos, encuestas,
                tasa(encuestas, marcados), promedioPonderado(eventos, true), promedioPonderado(eventos, false));
    }

    /** Promedio global ponderado por la cantidad de encuestas de cada evento (no un promedio de promedios). */
    private static Double promedioPonderado(List<EventoInforme> eventos, boolean satisfaccion) {
        double suma = 0;
        long n = 0;
        for (EventoInforme e : eventos) {
            Double promedio = satisfaccion ? e.satisfaccionPromedio() : e.utilidadPromedio();
            if (promedio != null && e.encuestasRecibidas() > 0) {
                suma += promedio * e.encuestasRecibidas();
                n += e.encuestasRecibidas();
            }
        }
        return n == 0 ? null : Math.round(suma / n * 100.0) / 100.0;
    }

    private static Double tasa(long encuestas, long marcados) {
        return marcados == 0 ? null : Math.round((double) encuestas / marcados * 1000.0) / 1000.0;
    }
}
