package com.duoc.cursos.evento.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.evento.dto.EventoRequest;
import com.duoc.cursos.evento.service.EventoService;
import com.duoc.cursos.sync.dto.AsistenciaFilaDto;
import com.duoc.cursos.sync.dto.ComentarioDto;
import com.duoc.cursos.sync.dto.EventoDto;
import com.duoc.cursos.sync.dto.ResumenEventoResponse;
import com.duoc.cursos.sync.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Supervisión y gestión de eventos por el Docente / Coordinador(a): quórum en vivo (caso §2.4 y §4.1), reporte
 * de asistencia y alta, edición y baja de eventos (§3.1). El acceso por rol (COORDINADOR) se declara en
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final SyncService syncService;
    private final EventoService eventoService;

    public EventoController(SyncService syncService, EventoService eventoService) {
        this.syncService = syncService;
        this.eventoService = eventoService;
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<ApiResponse<ResumenEventoResponse>> resumen(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.resumen(id), "Resumen del evento"));
    }

    @GetMapping("/{id}/asistencias")
    public ResponseEntity<ApiResponse<List<AsistenciaFilaDto>>> asistencias(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(eventoService.asistencias(id), "Asistencias del evento"));
    }

    @GetMapping("/{id}/comentarios")
    public ResponseEntity<ApiResponse<List<ComentarioDto>>> comentarios(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(eventoService.comentarios(id), "Comentarios del evento"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventoDto>> crear(@Valid @RequestBody EventoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(eventoService.crear(req), "Evento creado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventoDto>> actualizar(@PathVariable("id") Long id,
                                                             @Valid @RequestBody EventoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(eventoService.actualizar(id, req), "Evento actualizado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable("id") Long id) {
        eventoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Evento eliminado"));
    }
}
