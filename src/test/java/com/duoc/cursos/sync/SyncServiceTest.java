package com.duoc.cursos.sync;

import com.duoc.cursos.exception.RecursoNoEncontradoException;
import com.duoc.cursos.model.Asistente;
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
import com.duoc.cursos.sync.dto.LoteSyncResponse;
import com.duoc.cursos.sync.dto.MotivoSync;
import com.duoc.cursos.sync.dto.ResultadoEstado;
import com.duoc.cursos.sync.dto.ResultadoSyncItem;
import com.duoc.cursos.sync.dto.ResumenEventoResponse;
import com.duoc.cursos.sync.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyncService contra H2 real: idempotencia, éxitos y fallos parciales, y reintentos.
 *
 * NO es @Transactional a propósito: SyncService abre una transacción por ítem, y una transacción de
 * prueba las uniría todas en una, ocultando justo el comportamiento que se quiere comprobar.
 */
@SpringBootTest
class SyncServiceTest {

    private static final String SUB_MONITOR = "sub-monitor-1";

    @Autowired private SyncService service;
    @Autowired private ProyectoRepository proyectos;
    @Autowired private EventoRepository eventos;
    @Autowired private AsistenteRepository asistentes;
    @Autowired private AsistenciaRepository asistencias;
    @Autowired private EncuestaRepository encuestas;

    private Evento evento;
    private Asistente a1;
    private Asistente a2;
    private Asistente a3;

    @BeforeEach
    void setUp() {
        encuestas.deleteAll();
        asistencias.deleteAll();
        asistentes.deleteAll();
        eventos.deleteAll();
        proyectos.deleteAll();

        evento = eventos.save(SyncFixtures.evento(proyectos.save(SyncFixtures.proyecto())));
        a1 = asistentes.save(SyncFixtures.asistente("ASI-0001"));
        a2 = asistentes.save(SyncFixtures.asistente("ASI-0002"));
        a3 = asistentes.save(SyncFixtures.asistente("ASI-0003"));
    }

    // ------------------------------------------------------------------ helpers

    private AsistenciaSyncItem marcacion(String uuid, Long eventoId, Long asistenteId, Double lat, Double lon) {
        AsistenciaSyncItem item = new AsistenciaSyncItem();
        item.setUuid(uuid);
        item.setEventoId(eventoId);
        item.setAsistenteId(asistenteId);
        item.setMarcadoEn(Instant.parse("2026-10-28T15:04:11Z"));
        item.setLatitud(lat);
        item.setLongitud(lon);
        return item;
    }

    private AsistenciaSyncItem marcacionValida(String uuid, Asistente asistente) {
        return marcacion(uuid, evento.getId(), asistente.getId(), SyncFixtures.LAT_RECINTO, SyncFixtures.LON_RECINTO);
    }

    private EncuestaSyncItem encuesta(String uuid, String asistenciaUuid, Integer satisfaccion, Integer utilidad) {
        EncuestaSyncItem item = new EncuestaSyncItem();
        item.setUuid(uuid);
        item.setAsistenciaUuid(asistenciaUuid);
        item.setSatisfaccion(satisfaccion);
        item.setUtilidad(utilidad);
        item.setComentarios("Muy útil");
        item.setEnviadaEn(Instant.parse("2026-10-28T16:10:00Z"));
        return item;
    }

    private LoteSyncResponse subir(AsistenciaSyncItem... items) {
        return service.sincronizarAsistencias(List.of(items), SUB_MONITOR);
    }

    // ------------------------------------------------------------------ asistencias: creación y validación

    @Test
    @DisplayName("Una marcación válida se guarda con quién la subió, su hora real y la validación GPS")
    void asistencia_seGuardaConTodosSusDatos() {
        LoteSyncResponse r = subir(marcacion("u-1", evento.getId(), a1.getId(),
                SyncFixtures.LAT_RECINTO + 0.0003, SyncFixtures.LON_RECINTO));

        ResultadoSyncItem res = r.resultados().get(0);
        assertEquals(ResultadoEstado.ACEPTADO, res.estado());
        assertEquals(EstadoValidacion.VALIDA, res.estadoValidacion());
        assertNotNull(res.distanciaMetros());
        assertNotNull(res.id());

        var guardada = asistencias.findByUuid("u-1").orElseThrow();
        assertEquals(SUB_MONITOR, guardada.getRegistradoPorSub());
        assertEquals(Instant.parse("2026-10-28T15:04:11Z"), guardada.getMarcadoEn(),
                "debe conservar la hora real del dispositivo");
        assertNotNull(guardada.getRecibidoEn());
        assertEquals(EstadoValidacion.VALIDA, guardada.getEstadoValidacion());
    }

    @Test
    @DisplayName("GPS: fuera de radio y sin ubicación se GUARDAN clasificadas, nunca se rechazan")
    void gps_clasificaSinRechazar() {
        LoteSyncResponse r = subir(
                marcacion("u-lejos", evento.getId(), a1.getId(), SyncFixtures.LAT_RECINTO + 0.01, SyncFixtures.LON_RECINTO),
                marcacion("u-sin-gps", evento.getId(), a2.getId(), null, null));

        assertEquals(2, r.aceptados());
        assertEquals(0, r.rechazados());
        assertEquals(EstadoValidacion.FUERA_DE_RADIO, asistencias.findByUuid("u-lejos").orElseThrow().getEstadoValidacion());
        assertEquals(EstadoValidacion.SIN_UBICACION, asistencias.findByUuid("u-sin-gps").orElseThrow().getEstadoValidacion());
        assertNull(asistencias.findByUuid("u-sin-gps").orElseThrow().getDistanciaMetros());
    }

    @Test
    @DisplayName("Datos inválidos: se rechaza ESE ítem con los nombres de los campos, y el resto sigue")
    void itemInvalido_noTumbaElLote() {
        AsistenciaSyncItem latitudFueraDeRango = marcacion("u-lat", evento.getId(), a1.getId(), 95.0, 0.0);
        AsistenciaSyncItem sinUuid = marcacion(null, evento.getId(), a2.getId(), null, null);
        AsistenciaSyncItem sinEvento = marcacion("u-sin-evento", null, a2.getId(), null, null);
        AsistenciaSyncItem valido = marcacionValida("u-ok", a3);

        LoteSyncResponse r = service.sincronizarAsistencias(
                java.util.Arrays.asList(latitudFueraDeRango, sinUuid, sinEvento, null, valido), SUB_MONITOR);

        assertEquals(1, r.aceptados());
        assertEquals(4, r.rechazados());
        assertEquals(MotivoSync.DATOS_INVALIDOS, r.resultados().get(0).motivo());
        assertEquals("latitud", r.resultados().get(0).detalle());
        assertEquals("uuid", r.resultados().get(1).detalle());
        assertNull(r.resultados().get(1).uuid(), "no se refleja un uuid que no existe");
        assertEquals("eventoId", r.resultados().get(2).detalle());
        assertEquals(MotivoSync.DATOS_INVALIDOS, r.resultados().get(3).motivo(), "un ítem nulo también se informa");
        assertEquals(ResultadoEstado.ACEPTADO, r.resultados().get(4).estado());
        assertEquals(1, asistencias.count(), "solo el válido quedó guardado");
    }

    @Test
    @DisplayName("Cada resultado trae el índice del registro, para correlacionar aunque el uuid sea lo inválido")
    void resultados_incluyenElIndice() {
        LoteSyncResponse r = service.sincronizarAsistencias(
                Arrays.asList(marcacionValida("u-0", a1), marcacion(null, 1L, 1L, null, null), marcacionValida("u-2", a2)),
                SUB_MONITOR);

        assertEquals(List.of(0, 1, 2), r.resultados().stream().map(ResultadoSyncItem::indice).toList());
    }

    @Test
    @DisplayName("Un uuid desmesurado no se devuelve al cliente")
    void uuidDesmesurado_noSeRefleja() {
        LoteSyncResponse r = subir(marcacion("x".repeat(5000), evento.getId(), a1.getId(), null, null));

        assertEquals(MotivoSync.DATOS_INVALIDOS, r.resultados().get(0).motivo());
        assertNull(r.resultados().get(0).uuid());
    }

    // ------------------------------------------------------------------ asistencias: duplicados y reintentos

    @Test
    @DisplayName("Reintento: reenviar el mismo lote da DUPLICADO y no crea filas nuevas")
    void reintento_esIdempotente() {
        AsistenciaSyncItem[] lote = {marcacionValida("u-1", a1), marcacionValida("u-2", a2)};

        LoteSyncResponse primero = subir(lote);
        LoteSyncResponse segundo = subir(lote);

        assertEquals(2, primero.aceptados());
        assertEquals(0, segundo.aceptados());
        assertEquals(2, segundo.duplicados());
        assertEquals(MotivoSync.UUID_YA_REGISTRADO, segundo.resultados().get(0).motivo());
        assertEquals(primero.resultados().get(0).id(), segundo.resultados().get(0).id(),
                "el duplicado apunta a la fila original");
        assertEquals(2, asistencias.count());
    }

    @Test
    @DisplayName("Otro uuid para el mismo (evento, asistente) -> DUPLICADO / ASISTENTE_YA_MARCADO")
    void mismaParejaConOtroUuid() {
        subir(marcacionValida("u-1", a1));

        LoteSyncResponse r = subir(marcacionValida("u-otro", a1));

        assertEquals(ResultadoEstado.DUPLICADO, r.resultados().get(0).estado());
        assertEquals(MotivoSync.ASISTENTE_YA_MARCADO, r.resultados().get(0).motivo());
        assertEquals(1, asistencias.count());
    }

    @Test
    @DisplayName("El mismo uuid repetido dentro de un lote: el primero se acepta y el segundo es duplicado")
    void uuidRepetidoDentroDelLote() {
        LoteSyncResponse r = subir(marcacionValida("u-1", a1), marcacionValida("u-1", a1));

        assertEquals(ResultadoEstado.ACEPTADO, r.resultados().get(0).estado());
        assertEquals(ResultadoEstado.DUPLICADO, r.resultados().get(1).estado());
        assertEquals(1, asistencias.count());
    }

    @Test
    @DisplayName("Concurrencia: dos peticiones con el mismo uuid a la vez -> una acepta, otra duplicado, una fila")
    void concurrencia_mismoUuid() throws Exception {
        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<LoteSyncResponse>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                Callable<LoteSyncResponse> tarea = () -> {
                    salida.await();
                    return subir(marcacionValida("u-carrera", a1));
                };
                futuros.add(pool.submit(tarea));
            }
            salida.countDown();

            List<ResultadoEstado> estados = new ArrayList<>();
            for (Future<LoteSyncResponse> f : futuros) {
                estados.add(f.get(30, TimeUnit.SECONDS).resultados().get(0).estado());
            }

            assertEquals(1, estados.stream().filter(e -> e == ResultadoEstado.ACEPTADO).count(), estados.toString());
            assertEquals(1, estados.stream().filter(e -> e == ResultadoEstado.DUPLICADO).count(), estados.toString());
            assertEquals(1, asistencias.count());
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ asistencias: referencias y lotes mixtos

    @Test
    @DisplayName("Evento o asistente inexistentes -> RECHAZADO con motivo propio")
    void referenciasInexistentes() {
        LoteSyncResponse r = subir(
                marcacion("u-1", 999_999L, a1.getId(), null, null),
                marcacion("u-2", evento.getId(), 999_999L, null, null));

        assertEquals(MotivoSync.EVENTO_NO_ENCONTRADO, r.resultados().get(0).motivo());
        assertEquals(MotivoSync.ASISTENTE_NO_ENCONTRADO, r.resultados().get(1).motivo());
        assertEquals(0, asistencias.count());
    }

    @Test
    @DisplayName("Lote mixto (1 válido, 1 duplicado, 1 inexistente, 1 válido): 200 lógico y los válidos persisten")
    void loteMixto_fallosParciales() {
        subir(marcacionValida("u-previo", a2));

        LoteSyncResponse r = subir(
                marcacionValida("u-nuevo-1", a1),
                marcacionValida("u-previo", a2),
                marcacion("u-sin-evento", 999_999L, a3.getId(), null, null),
                marcacionValida("u-nuevo-2", a3));

        assertEquals(2, r.aceptados());
        assertEquals(1, r.duplicados());
        assertEquals(1, r.rechazados());
        assertEquals(
                List.of(ResultadoEstado.ACEPTADO, ResultadoEstado.DUPLICADO, ResultadoEstado.RECHAZADO, ResultadoEstado.ACEPTADO),
                r.resultados().stream().map(ResultadoSyncItem::estado).toList());
        assertTrue(asistencias.findByUuid("u-nuevo-1").isPresent(), "el primero válido debe haber persistido");
        assertTrue(asistencias.findByUuid("u-nuevo-2").isPresent(), "el último válido debe haber persistido");
        assertEquals(3, asistencias.count());
    }

    // ------------------------------------------------------------------ encuestas

    @Test
    @DisplayName("Encuesta válida: se guarda colgada de su asistencia")
    void encuesta_seGuarda() {
        subir(marcacionValida("u-a1", a1));

        LoteSyncResponse r = service.sincronizarEncuestas(List.of(encuesta("e-1", "u-a1", 5, 4)));

        assertEquals(ResultadoEstado.ACEPTADO, r.resultados().get(0).estado());
        var guardada = encuestas.findByUuid("e-1").orElseThrow();
        assertEquals(5, guardada.getSatisfaccion());
        assertEquals(4, guardada.getUtilidad());
        assertEquals(asistencias.findByUuid("u-a1").orElseThrow().getId(), guardada.getAsistencia().getId());
    }

    @Test
    @DisplayName("Encuesta antes que su asistencia: RECHAZADO transitorio, y reintentando después se acepta")
    void encuesta_antesQueLaAsistencia() {
        LoteSyncResponse antes = service.sincronizarEncuestas(List.of(encuesta("e-1", "u-a1", 5, 4)));
        assertEquals(MotivoSync.ASISTENCIA_NO_ENCONTRADA, antes.resultados().get(0).motivo());
        assertEquals(0, encuestas.count());

        subir(marcacionValida("u-a1", a1));
        LoteSyncResponse despues = service.sincronizarEncuestas(List.of(encuesta("e-1", "u-a1", 5, 4)));

        assertEquals(ResultadoEstado.ACEPTADO, despues.resultados().get(0).estado());
        assertEquals(1, encuestas.count());
    }

    @Test
    @DisplayName("Encuesta: reintento del mismo uuid y segunda encuesta para la misma asistencia son duplicados")
    void encuesta_duplicados() {
        subir(marcacionValida("u-a1", a1));
        service.sincronizarEncuestas(List.of(encuesta("e-1", "u-a1", 5, 4)));

        LoteSyncResponse r = service.sincronizarEncuestas(List.of(
                encuesta("e-1", "u-a1", 5, 4),
                encuesta("e-2", "u-a1", 1, 1)));

        assertEquals(MotivoSync.UUID_YA_REGISTRADO, r.resultados().get(0).motivo());
        assertEquals(MotivoSync.ENCUESTA_YA_REGISTRADA, r.resultados().get(1).motivo());
        assertEquals(2, r.duplicados());
        assertEquals(1, encuestas.count());
        assertEquals(5, encuestas.findByUuid("e-1").orElseThrow().getSatisfaccion(), "no se pisa la original");
    }

    @Test
    @DisplayName("Encuesta con la escala fuera de 1-5 -> DATOS_INVALIDOS por ítem")
    void encuesta_escalaInvalida() {
        subir(marcacionValida("u-a1", a1));
        subir(marcacionValida("u-a2", a2));

        LoteSyncResponse r = service.sincronizarEncuestas(List.of(
                encuesta("e-mala", "u-a1", 6, 4),
                encuesta("e-sin-utilidad", "u-a1", 3, null),
                encuesta("e-buena", "u-a2", 3, 3)));

        assertEquals("satisfaccion", r.resultados().get(0).detalle());
        assertEquals("utilidad", r.resultados().get(1).detalle());
        assertEquals(ResultadoEstado.ACEPTADO, r.resultados().get(2).estado());
        assertEquals(1, encuestas.count());
    }

    // ------------------------------------------------------------------ catálogo

    @Test
    @DisplayName("Catálogo completo sin 'desde'; incremental con 'desde' (inclusive)")
    void catalogo_incremental() {
        CatalogoResponse completo = service.catalogo(null);
        assertEquals(1, completo.proyectos().size());
        assertEquals(1, completo.eventos().size());
        assertEquals(evento.getProyecto().getId(), completo.eventos().get(0).proyectoId());
        assertEquals(3, completo.asistentes().size());

        CatalogoResponse nada = service.catalogo(completo.servidorEn().plusSeconds(60));
        assertTrue(nada.proyectos().isEmpty());
        assertTrue(nada.eventos().isEmpty());
        assertTrue(nada.asistentes().isEmpty());
        assertNotNull(nada.servidorEn());

        CatalogoResponse todo = service.catalogo(Instant.EPOCH);
        assertEquals(3, todo.asistentes().size());
    }

    @Test
    @DisplayName("El catálogo no expone datos personales de los asistentes")
    void catalogo_asistenteSinDatosPersonales() {
        Set<String> campos = Arrays.stream(AsistenteDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "codigo", "tipo", "estadoInscripcion", "actualizadoEn"), campos);
    }

    // ------------------------------------------------------------------ resumen

    @Test
    @DisplayName("Resumen: conteos por validación, encuestas y promedios sobre un conjunto conocido")
    void resumen_conjuntoConocido() {
        AsistenciaSyncItem valida = marcacionValida("u-1", a1);
        valida.setMarcadoEn(Instant.parse("2026-10-28T15:01:00Z"));
        AsistenciaSyncItem lejos = marcacion("u-2", evento.getId(), a2.getId(), SyncFixtures.LAT_RECINTO + 0.01, SyncFixtures.LON_RECINTO);
        lejos.setMarcadoEn(Instant.parse("2026-10-28T15:20:00Z"));
        AsistenciaSyncItem sinGps = marcacion("u-3", evento.getId(), a3.getId(), null, null);
        sinGps.setMarcadoEn(Instant.parse("2026-10-28T15:42:00Z"));
        subir(valida, lejos, sinGps);
        service.sincronizarEncuestas(List.of(encuesta("e-1", "u-1", 5, 4), encuesta("e-2", "u-2", 3, 2)));

        ResumenEventoResponse r = service.resumen(evento.getId());

        assertEquals(3, r.totalMarcados());
        assertEquals(1, r.validas());
        assertEquals(1, r.fueraDeRadio());
        assertEquals(1, r.sinUbicacion());
        assertEquals(r.totalMarcados(), r.validas() + r.fueraDeRadio() + r.sinUbicacion(), "el desglose debe cuadrar");
        assertEquals(2, r.encuestasRecibidas());
        assertEquals(4.0, r.satisfaccionPromedio());
        assertEquals(3.0, r.utilidadPromedio());
        assertEquals(Instant.parse("2026-10-28T15:01:00Z"), r.primeraMarcacion());
        assertEquals(Instant.parse("2026-10-28T15:42:00Z"), r.ultimaMarcacion());
        assertEquals(30, r.cuposMaximos());
    }

    @Test
    @DisplayName("Resumen de un evento sin marcaciones: ceros y promedios nulos, no error")
    void resumen_sinMarcaciones() {
        ResumenEventoResponse r = service.resumen(evento.getId());

        assertEquals(0, r.totalMarcados());
        assertEquals(0, r.encuestasRecibidas());
        assertNull(r.satisfaccionPromedio());
        assertNull(r.primeraMarcacion());
    }

    @Test
    @DisplayName("Resumen de un evento inexistente -> RecursoNoEncontradoException")
    void resumen_eventoInexistente() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.resumen(999_999L));
    }
}
