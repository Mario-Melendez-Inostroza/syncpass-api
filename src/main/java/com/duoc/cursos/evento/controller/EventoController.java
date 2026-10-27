package com.duoc.cursos.evento.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.sync.dto.ResumenEventoResponse;
import com.duoc.cursos.sync.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quórum en vivo para la supervisión docente (caso §2.4 y §4.1). El acceso por rol (COORDINADOR)
 * se declara en SecurityConfig.
 */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final SyncService syncService;

    public EventoController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<ApiResponse<ResumenEventoResponse>> resumen(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.resumen(id), "Resumen del evento"));
    }
}
