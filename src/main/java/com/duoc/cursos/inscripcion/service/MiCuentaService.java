package com.duoc.cursos.inscripcion.service;

import com.duoc.cursos.exception.ConflictoException;
import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.inscripcion.dto.MiPerfilDto;
import com.duoc.cursos.inscripcion.dto.TallerDto;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Inscripcion;
import com.duoc.cursos.model.enums.EstadoInscripcion;
import com.duoc.cursos.model.enums.TipoParticipante;
import com.duoc.cursos.repository.AsistenteRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.InscripcionRepository;
import com.duoc.cursos.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La cuenta del Asistente / Beneficiario Comunitario (caso §3.1): registrarse, ver los talleres e inscribirse.
 * Sin datos personales en la base: el asistente es un código sintético vinculado al sub de Cognito (caso §5.1).
 */
@Service
public class MiCuentaService {

    static final String PREFIJO_CODIGO = "REG-";
    /** Un taller sigue apareciendo unas horas después de su inicio (por si empezó tarde). */
    static final Duration GRACIA = Duration.ofHours(6);

    private final AuthService authService;
    private final AsistenteRepository asistentes;
    private final EventoRepository eventos;
    private final InscripcionRepository inscripciones;

    public MiCuentaService(AuthService authService, AsistenteRepository asistentes, EventoRepository eventos,
                           InscripcionRepository inscripciones) {
        this.authService = authService;
        this.asistentes = asistentes;
        this.eventos = eventos;
        this.inscripciones = inscripciones;
    }

    /** Crea la cuenta (Cognito, grupo ASISTENTE) y su asistente sintético. Si falla la base, revierte la cuenta. */
    public MiPerfilDto registrar(String email, String password, TipoParticipante tipo) {
        String sub = authService.crearCuentaEnGrupo(email, password, "Asistente", AuthService.ROL_ASISTENTE);
        try {
            Asistente a = new Asistente();
            a.setCodigo(PREFIJO_CODIGO + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
            a.setTipo(tipo);
            a.setEstadoInscripcion(EstadoInscripcion.INSCRITO);
            a.setUsuarioSub(sub);
            return MiPerfilDto.de(asistentes.saveAndFlush(a));
        } catch (RuntimeException ex) {
            authService.revertirCuenta(email, sub, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public MiPerfilDto perfil(String sub) {
        return MiPerfilDto.de(asistenteDe(sub));
    }

    @Transactional(readOnly = true)
    public List<TallerDto> talleres(String sub) {
        Asistente yo = asistenteDe(sub);
        Instant desde = Instant.now().minus(GRACIA);
        Map<Long, Long> inscritosPorEvento = new HashMap<>();
        for (Object[] fila : inscripciones.contarPorEvento()) inscritosPorEvento.put((Long) fila[0], (Long) fila[1]);
        Set<Long> mios = new HashSet<>(inscripciones.eventosDe(yo.getId()));
        return eventos.findAll().stream()
                .filter(e -> !e.getFechaHoraProgramada().isBefore(desde))
                .sorted(Comparator.comparing(Evento::getFechaHoraProgramada))
                .map(e -> {
                    long inscritos = inscritosPorEvento.getOrDefault(e.getId(), 0L);
                    Integer libres = e.getCuposMaximos() == null ? null : (int) Math.max(0, e.getCuposMaximos() - inscritos);
                    return new TallerDto(e.getId(), e.getNombre(), e.getProyecto().getTitulo(), e.getFechaHoraProgramada(),
                            e.getRecinto(), e.getCuposMaximos(), inscritos, libres, mios.contains(e.getId()));
                })
                .toList();
    }

    @Transactional
    public void inscribir(String sub, Long eventoId) {
        Asistente yo = asistenteDe(sub);
        Evento evento = eventos.findById(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El taller no existe."));
        if (evento.getFechaHoraProgramada().isBefore(Instant.now().minus(GRACIA))) {
            throw new ConflictoException("Este taller ya ocurrió.");
        }
        if (inscripciones.existsByAsistenteIdAndEventoId(yo.getId(), eventoId)) {
            throw new ConflictoException("Ya estás inscrito en este taller.");
        }
        if (evento.getCuposMaximos() != null && inscripciones.countByEventoId(eventoId) >= evento.getCuposMaximos()) {
            throw new ConflictoException("El taller no tiene cupos disponibles.");
        }
        Inscripcion i = new Inscripcion();
        i.setAsistente(yo);
        i.setEvento(evento);
        try {
            inscripciones.saveAndFlush(i);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("Ya estás inscrito en este taller.");
        }
    }

    /** Idempotente: si no estaba inscrito, no pasa nada. */
    @Transactional
    public void cancelar(String sub, Long eventoId) {
        Asistente yo = asistenteDe(sub);
        inscripciones.findByAsistenteIdAndEventoId(yo.getId(), eventoId).ifPresent(inscripciones::delete);
    }

    private Asistente asistenteDe(String sub) {
        return asistentes.findByUsuarioSub(sub)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tu cuenta no tiene un perfil de asistente."));
    }
}
