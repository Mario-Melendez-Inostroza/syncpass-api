package com.duoc.cursos.inscripcion.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.inscripcion.dto.MiPerfilDto;
import com.duoc.cursos.inscripcion.dto.TallerDto;
import com.duoc.cursos.inscripcion.service.MiCuentaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lo que hace el Asistente con su cuenta. El acceso (grupo ASISTENTE) se declara en SecurityConfig. */
@RestController
@RequestMapping("/api/mi")
public class MiCuentaController {

    private final MiCuentaService servicio;

    public MiCuentaController(MiCuentaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/perfil")
    public ResponseEntity<ApiResponse<MiPerfilDto>> perfil(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(servicio.perfil(jwt.getSubject()), "Mi perfil"));
    }

    @GetMapping("/talleres")
    public ResponseEntity<ApiResponse<List<TallerDto>>> talleres(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(servicio.talleres(jwt.getSubject()), "Talleres"));
    }

    @PostMapping("/talleres/{eventoId}/inscripcion")
    public ResponseEntity<ApiResponse<Void>> inscribir(@AuthenticationPrincipal Jwt jwt, @PathVariable("eventoId") Long eventoId) {
        servicio.inscribir(jwt.getSubject(), eventoId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null, "Inscripción realizada"));
    }

    @DeleteMapping("/talleres/{eventoId}/inscripcion")
    public ResponseEntity<ApiResponse<Void>> cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable("eventoId") Long eventoId) {
        servicio.cancelar(jwt.getSubject(), eventoId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Inscripción cancelada"));
    }
}
