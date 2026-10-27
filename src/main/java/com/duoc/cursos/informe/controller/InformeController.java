package com.duoc.cursos.informe.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.informe.dto.InformeDtos.InformeResponse;
import com.duoc.cursos.informe.service.InformeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Informes de impacto para el Docente / Coordinador(a). El acceso por rol (COORDINADOR) se declara en SecurityConfig. */
@RestController
@RequestMapping("/api/informes")
public class InformeController {

    private final InformeService informeService;

    public InformeController(InformeService informeService) {
        this.informeService = informeService;
    }

    /** Sin {@code proyectoId} devuelve todos los proyectos. */
    @GetMapping
    public ResponseEntity<ApiResponse<InformeResponse>> informe(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId) {
        return ResponseEntity.ok(ApiResponse.ok(informeService.generar(proyectoId), "Informe de impacto"));
    }
}
