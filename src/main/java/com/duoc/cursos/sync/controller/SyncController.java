package com.duoc.cursos.sync.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.sync.dto.CatalogoResponse;
import com.duoc.cursos.sync.dto.LoteAsistentesResponse;
import com.duoc.cursos.sync.dto.LoteSyncResponse;
import com.duoc.cursos.sync.dto.SincronizarAsistenciasRequest;
import com.duoc.cursos.sync.dto.SincronizarAsistentesRequest;
import com.duoc.cursos.sync.dto.SincronizarEncuestasRequest;
import com.duoc.cursos.sync.service.AsistenteSyncService;
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
    private final AsistenteSyncService asistenteSyncService;

    public SyncController(SyncService syncService, AsistenteSyncService asistenteSyncService) {
        this.syncService = syncService;
        this.asistenteSyncService = asistenteSyncService;
    }

    @GetMapping("/catalogo")
    public ResponseEntity<ApiResponse<CatalogoResponse>> catalogo(
            @RequestParam(value = "desde", required = false) Instant desde) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.catalogo(desde), "Catálogo sincronizado"));
    }

    /** Alta de asistentes llegados sin inscripción previa (caso §3.1). Debe subirse antes que sus asistencias. */
    @PostMapping("/asistentes")
    public ResponseEntity<ApiResponse<LoteAsistentesResponse>> asistentes(
            @Valid @RequestBody SincronizarAsistentesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(asistenteSyncService.sincronizar(req.getRegistros()), "Lote procesado"));
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
