package com.duoc.cursos.asistente.controller;

import com.duoc.cursos.asistente.dto.AsistenteAltaRequest;
import com.duoc.cursos.asistente.dto.AsistenteEdicionRequest;
import com.duoc.cursos.asistente.dto.AsistenteFilaDto;
import com.duoc.cursos.asistente.service.AsistenteService;
import com.duoc.cursos.common.dto.ApiResponse;
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

/** Gestión de asistentes por el Docente / Coordinador(a). El acceso por rol (COORDINADOR) se declara en SecurityConfig. */
@RestController
@RequestMapping("/api/asistentes")
public class AsistenteController {

    private final AsistenteService asistenteService;

    public AsistenteController(AsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AsistenteFilaDto>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(asistenteService.listar(), "Asistentes"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<AsistenteFilaDto>>> crear(@Valid @RequestBody AsistenteAltaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(asistenteService.crear(req), "Asistentes creados"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AsistenteFilaDto>> actualizar(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody AsistenteEdicionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(asistenteService.actualizar(id, req), "Asistente actualizado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable("id") Long id) {
        asistenteService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Asistente eliminado"));
    }
}
