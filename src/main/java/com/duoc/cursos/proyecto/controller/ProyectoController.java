package com.duoc.cursos.proyecto.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.proyecto.dto.ProyectoRequest;
import com.duoc.cursos.proyecto.service.ProyectoService;
import com.duoc.cursos.sync.dto.ProyectoDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de proyectos por el Docente / Coordinador(a) (caso §3.1). El acceso por rol (COORDINADOR) se declara
 * en SecurityConfig. La lectura sigue siendo GET /api/sync/catalogo.
 */
@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {

    private final ProyectoService proyectoService;

    public ProyectoController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProyectoDto>> crear(@Valid @RequestBody ProyectoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(proyectoService.crear(req), "Proyecto creado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProyectoDto>> actualizar(@PathVariable("id") Long id,
                                                               @Valid @RequestBody ProyectoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(proyectoService.actualizar(id, req), "Proyecto actualizado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable("id") Long id) {
        proyectoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Proyecto eliminado"));
    }
}
