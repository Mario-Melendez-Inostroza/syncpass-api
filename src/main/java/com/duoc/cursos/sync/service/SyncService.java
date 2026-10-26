package com.duoc.cursos.sync.service;

import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Encuesta;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.enums.EstadoValidacion;
import com.duoc.cursos.repository.AsistenciaRepository;
import com.duoc.cursos.repository.AsistenteRepository;
import com.duoc.cursos.repository.EncuestaRepository;
import com.duoc.cursos.repository.EventoRepository;
import com.duoc.cursos.repository.ProyectoRepository;
import com.duoc.cursos.sync.dto.AsistenciaSyncItem;
import com.duoc.cursos.sync.dto.AsistenteDto;
import com.duoc.cursos.sync.dto.CatalogoResponse;
import com.duoc.cursos.sync.dto.EncuestaSyncItem;
import com.duoc.cursos.sync.dto.EventoDto;
import com.duoc.cursos.sync.dto.LoteSyncResponse;
import com.duoc.cursos.sync.dto.MotivoSync;
import com.duoc.cursos.sync.dto.ProyectoDto;
import com.duoc.cursos.sync.dto.ResultadoSyncItem;
import com.duoc.cursos.sync.dto.ResumenEventoResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sincronización por lotes entre el móvil (offline-first) y el servidor.
 *
 * Reglas que sostienen todo el diseño:
 *  - Cada registro es independiente: uno inválido o duplicado no tumba ni revierte a los demás. Por eso
 *    cada ítem corre en SU PROPIA transacción (TransactionTemplate), no en una transacción del lote.
 *  - Reenviar un lote es inocuo: el uuid generado por el teléfono es la clave de idempotencia.
 *  - ACEPTADO y DUPLICADO significan que el registro ya existe en el servidor.
 *  - El usuario que registra sale del JWT; nunca del cuerpo.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final int UUID_MAX = 36;

    private final ProyectoRepository proyectoRepository;
    private final EventoRepository eventoRepository;
    private final AsistenteRepository asistenteRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final EncuestaRepository encuestaRepository;
    private final Validator validator;
    private final TransactionTemplate transaccion;

    public SyncService(ProyectoRepository proyectoRepository,
                       EventoRepository eventoRepository,
                       AsistenteRepository asistenteRepository,
                       AsistenciaRepository asistenciaRepository,
                       EncuestaRepository encuestaRepository,
                       Validator validator,
                       PlatformTransactionManager transactionManager) {
        this.proyectoRepository = proyectoRepository;
        this.eventoRepository = eventoRepository;
        this.asistenteRepository = asistenteRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.encuestaRepository = encuestaRepository;
        this.validator = validator;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------ catálogo

    /**
     * @param desde si es null devuelve todo; si no, lo modificado desde ese instante (inclusive).
     */
    @Transactional(readOnly = true)
    public CatalogoResponse catalogo(Instant desde) {
        // Se toma ANTES de consultar: una fila modificada mientras se arma la respuesta queda con una
        // marca >= servidorEn y vuelve en la próxima sincronización, en vez de perderse.
        Instant servidorEn = Instant.now();

        var proyectos = desde == null ? proyectoRepository.findAll()
                : proyectoRepository.findByActualizadoEnGreaterThanEqual(desde);
        var eventos = desde == null ? eventoRepository.findAll()
                : eventoRepository.findByActualizadoEnGreaterThanEqual(desde);
        var asistentes = desde == null ? asistenteRepository.findAll()
                : asistenteRepository.findByActualizadoEnGreaterThanEqual(desde);

        return new CatalogoResponse(servidorEn,
                proyectos.stream().map(ProyectoDto::de).toList(),
                eventos.stream().map(EventoDto::de).toList(),
                asistentes.stream().map(AsistenteDto::de).toList());
    }

    // ------------------------------------------------------------------ asistencias

    public LoteSyncResponse sincronizarAsistencias(List<AsistenciaSyncItem> items, String registradoPorSub) {
        List<ResultadoSyncItem> resultados = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            resultados.add(procesarAsistencia(i, items.get(i), registradoPorSub));
        }
        LoteSyncResponse respuesta = LoteSyncResponse.de(resultados);
        log.info("Lote de asistencias: {} aceptadas, {} duplicadas, {} rechazadas",
                respuesta.aceptados(), respuesta.duplicados(), respuesta.rechazados());
        return respuesta;
    }

    private ResultadoSyncItem procesarAsistencia(int indice, AsistenciaSyncItem item, String sub) {
        if (item == null) {
            return ResultadoSyncItem.invalido(indice, null, "registro");
        }
        String camposInvalidos = camposInvalidos(item);
        if (camposInvalidos != null) {
            return ResultadoSyncItem.invalido(indice, uuidSeguro(item.getUuid()), camposInvalidos);
        }

        try {
            return transaccion.execute(status -> guardarAsistencia(indice, item, sub));
        } catch (DataIntegrityViolationException ex) {
            // Otra petición concurrente (p. ej. un reintento que se cruzó) ganó la carrera por la
            // restricción única. Se resuelve releyendo: ya existe, así que es un duplicado.
            return resolverCarreraAsistencia(indice, item, ex);
        }
    }

    private ResultadoSyncItem guardarAsistencia(int indice, AsistenciaSyncItem item, String sub) {
        var porUuid = asistenciaRepository.findByUuid(item.getUuid());
        if (porUuid.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.UUID_YA_REGISTRADO,
                    porUuid.get().getId());
        }

        Evento evento = eventoRepository.findById(item.getEventoId()).orElse(null);
        if (evento == null) {
            return ResultadoSyncItem.rechazado(indice, item.getUuid(), MotivoSync.EVENTO_NO_ENCONTRADO);
        }
        Asistente asistente = asistenteRepository.findById(item.getAsistenteId()).orElse(null);
        if (asistente == null) {
            return ResultadoSyncItem.rechazado(indice, item.getUuid(), MotivoSync.ASISTENTE_NO_ENCONTRADO);
        }

        var porPareja = asistenciaRepository.findByEventoIdAndAsistenteId(evento.getId(), asistente.getId());
        if (porPareja.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.ASISTENTE_YA_MARCADO,
                    porPareja.get().getId());
        }

        Geo.Clasificacion geo = Geo.clasificar(item.getLatitud(), item.getLongitud(), evento);

        Asistencia asistencia = new Asistencia();
        asistencia.setUuid(item.getUuid());
        asistencia.setEvento(evento);
        asistencia.setAsistente(asistente);
        asistencia.setMarcadoEn(item.getMarcadoEn());
        asistencia.setLatitud(item.getLatitud());
        asistencia.setLongitud(item.getLongitud());
        asistencia.setDistanciaMetros(geo.distanciaMetros());
        asistencia.setEstadoValidacion(geo.estado());
        asistencia.setRegistradoPorSub(sub);
        asistencia.setRecibidoEn(Instant.now());

        // saveAndFlush: la violación de la restricción única debe saltar AQUÍ, dentro de la transacción
        // del ítem, y no al hacer commit, donde ya no habría forma de atribuirla a este registro.
        Asistencia guardada = asistenciaRepository.saveAndFlush(asistencia);
        return ResultadoSyncItem.aceptado(indice, item.getUuid(), guardada.getId(),
                geo.estado(), geo.distanciaMetros());
    }

    private ResultadoSyncItem resolverCarreraAsistencia(int indice, AsistenciaSyncItem item,
                                                        DataIntegrityViolationException causa) {
        var porUuid = asistenciaRepository.findByUuid(item.getUuid());
        if (porUuid.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.UUID_YA_REGISTRADO,
                    porUuid.get().getId());
        }
        var porPareja = asistenciaRepository.findByEventoIdAndAsistenteId(item.getEventoId(), item.getAsistenteId());
        if (porPareja.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.ASISTENTE_YA_MARCADO,
                    porPareja.get().getId());
        }
        // Una violación de integridad que no es ninguna de las dos restricciones únicas conocidas.
        log.warn("Integridad violada al guardar una asistencia (indice={}): {}", indice,
                causa.getClass().getSimpleName());
        return ResultadoSyncItem.invalido(indice, item.getUuid(), "integridad");
    }

    // ------------------------------------------------------------------ encuestas

    public LoteSyncResponse sincronizarEncuestas(List<EncuestaSyncItem> items) {
        List<ResultadoSyncItem> resultados = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            resultados.add(procesarEncuesta(i, items.get(i)));
        }
        LoteSyncResponse respuesta = LoteSyncResponse.de(resultados);
        log.info("Lote de encuestas: {} aceptadas, {} duplicadas, {} rechazadas",
                respuesta.aceptados(), respuesta.duplicados(), respuesta.rechazados());
        return respuesta;
    }

    private ResultadoSyncItem procesarEncuesta(int indice, EncuestaSyncItem item) {
        if (item == null) {
            return ResultadoSyncItem.invalido(indice, null, "registro");
        }
        String camposInvalidos = camposInvalidos(item);
        if (camposInvalidos != null) {
            return ResultadoSyncItem.invalido(indice, uuidSeguro(item.getUuid()), camposInvalidos);
        }

        try {
            return transaccion.execute(status -> guardarEncuesta(indice, item));
        } catch (DataIntegrityViolationException ex) {
            return resolverCarreraEncuesta(indice, item, ex);
        }
    }

    private ResultadoSyncItem guardarEncuesta(int indice, EncuestaSyncItem item) {
        var porUuid = encuestaRepository.findByUuid(item.getUuid());
        if (porUuid.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.UUID_YA_REGISTRADO,
                    porUuid.get().getId());
        }

        Asistencia asistencia = asistenciaRepository.findByUuid(item.getAsistenciaUuid()).orElse(null);
        if (asistencia == null) {
            // Transitorio: la encuesta pudo llegar antes que su asistencia. El cliente la deja PENDIENTE.
            return ResultadoSyncItem.rechazado(indice, item.getUuid(), MotivoSync.ASISTENCIA_NO_ENCONTRADA);
        }

        var porAsistencia = encuestaRepository.findByAsistenciaId(asistencia.getId());
        if (porAsistencia.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.ENCUESTA_YA_REGISTRADA,
                    porAsistencia.get().getId());
        }

        Encuesta encuesta = new Encuesta();
        encuesta.setUuid(item.getUuid());
        encuesta.setAsistencia(asistencia);
        encuesta.setSatisfaccion(item.getSatisfaccion());
        encuesta.setUtilidad(item.getUtilidad());
        encuesta.setComentarios(item.getComentarios());
        encuesta.setEnviadaEn(item.getEnviadaEn());
        encuesta.setRecibidaEn(Instant.now());

        Encuesta guardada = encuestaRepository.saveAndFlush(encuesta);
        return ResultadoSyncItem.aceptado(indice, item.getUuid(), guardada.getId(), null, null);
    }

    private ResultadoSyncItem resolverCarreraEncuesta(int indice, EncuestaSyncItem item,
                                                      DataIntegrityViolationException causa) {
        var porUuid = encuestaRepository.findByUuid(item.getUuid());
        if (porUuid.isPresent()) {
            return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.UUID_YA_REGISTRADO,
                    porUuid.get().getId());
        }
        var asistencia = asistenciaRepository.findByUuid(item.getAsistenciaUuid());
        if (asistencia.isPresent()) {
            var porAsistencia = encuestaRepository.findByAsistenciaId(asistencia.get().getId());
            if (porAsistencia.isPresent()) {
                return ResultadoSyncItem.duplicado(indice, item.getUuid(), MotivoSync.ENCUESTA_YA_REGISTRADA,
                        porAsistencia.get().getId());
            }
        }
        log.warn("Integridad violada al guardar una encuesta (indice={}): {}", indice,
                causa.getClass().getSimpleName());
        return ResultadoSyncItem.invalido(indice, item.getUuid(), "integridad");
    }

    // ------------------------------------------------------------------ resumen

    @Transactional(readOnly = true)
    public ResumenEventoResponse resumen(Long eventoId) {
        Evento evento = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento no existe"));

        return new ResumenEventoResponse(
                evento.getId(),
                evento.getNombre(),
                evento.getCuposMaximos(),
                asistenciaRepository.countByEventoId(eventoId),
                asistenciaRepository.countByEventoIdAndEstadoValidacion(eventoId, EstadoValidacion.VALIDA),
                asistenciaRepository.countByEventoIdAndEstadoValidacion(eventoId, EstadoValidacion.FUERA_DE_RADIO),
                asistenciaRepository.countByEventoIdAndEstadoValidacion(eventoId, EstadoValidacion.SIN_UBICACION),
                encuestaRepository.contarPorEvento(eventoId),
                redondear(encuestaRepository.promedioSatisfaccion(eventoId)),
                redondear(encuestaRepository.promedioUtilidad(eventoId)),
                asistenciaRepository.primeraMarcacion(eventoId),
                asistenciaRepository.ultimaMarcacion(eventoId));
    }

    // ------------------------------------------------------------------ utilidades

    /** Nombres de los campos inválidos (ordenados, sin valores), o null si el ítem es válido. */
    private <T> String camposInvalidos(T item) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(item);
        if (violaciones.isEmpty()) {
            return null;
        }
        return violaciones.stream()
                .map(v -> v.getPropertyPath().toString())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    /** Devuelve el uuid solo si es razonable, para no reflejar de vuelta una cadena enorme. */
    private static String uuidSeguro(String uuid) {
        return uuid != null && uuid.length() <= UUID_MAX ? uuid : null;
    }

    private static Double redondear(Double valor) {
        return valor == null ? null : Math.round(valor * 100.0) / 100.0;
    }
}
