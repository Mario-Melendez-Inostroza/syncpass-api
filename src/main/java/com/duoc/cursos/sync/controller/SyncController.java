package com.duoc.cursos.sync.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.sync.dto.CatalogoResponse;
import com.duoc.cursos.sync.dto.LoteSyncResponse;
import com.duoc.cursos.sync.dto.SincronizarAsistenciasRequest;
import com.duoc.cursos.sync.dto.SincronizarEncuestasRequest;
import com.duoc.cursos.sync.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Sincronización con el móvil offline-first (caso §3.4). Requiere estar autenticado, sin rol concreto:
 * tanto el docente como el estudiante voluntario registran asistencia en terreno (§3.1).
 *
 * Los lotes responden 200 aunque contengan registros rechazados: el resultado de cada uno va en el
 * cuerpo. Solo un lote mal formado (vacío, demasiado grande, JSON ilegible) responde 400.
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/catalogo")
    public ResponseEntity<ApiResponse<CatalogoResponse>> catalogo(
            @RequestParam(value = "desde", required = false) Instant desde) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.catalogo(desde), "Catálogo sincronizado"));
    }

    @PostMapping("/asistencias")
    public ResponseEntity<ApiResponse<LoteSyncResponse>> asistencias(
            @Valid @RequestBody SincronizarAsistenciasRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        // El usuario que registra se toma del token, jamás del cuerpo.
        LoteSyncResponse respuesta = syncService.sincronizarAsistencias(req.getRegistros(), jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok(respuesta, "Lote procesado"));
    }

    @PostMapping("/encuestas")
    public ResponseEntity<ApiResponse<LoteSyncResponse>> encuestas(
            @Valid @RequestBody SincronizarEncuestasRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.sincronizarEncuestas(req.getRegistros()), "Lote procesado"));
    }
}
