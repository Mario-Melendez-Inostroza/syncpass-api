package com.duoc.cursos.controller;

import com.duoc.cursos.model.Usuario;
import com.duoc.cursos.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<Usuario> registrar(@Valid @RequestBody RegistroRequest req) {
        Usuario usuario = authService.registrar(req.getEmail(), req.getPassword(), req.getNombre(), req.getRoles());
        return ResponseEntity.ok(usuario);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthenticationResultType result = authService.login(req.getEmail(), req.getPassword());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.accessToken());
        response.setIdToken(result.idToken());
        response.setRefreshToken(result.refreshToken());
        response.setExpiresIn(result.expiresIn());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String accessToken = bearerToken.substring(7);
        authService.logout(accessToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/olvide-contrasena/solicitar")
    public ResponseEntity<Void> solicitarPassword(@Valid @RequestBody SolicitudPasswordRequest req) {
        authService.solicitarRecuperacionContrasena(req.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/olvide-contrasena/confirmar")
    public ResponseEntity<Void> confirmarPassword(@Valid @RequestBody ConfirmarPasswordRequest req) {
        authService.confirmarRecuperacionContrasena(req.getEmail(), req.getCodigo(), req.getNuevaContrasena());
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // DATA TRANSFER OBJECTS (DTOs)
    // ==========================================
    @Data
    public static class RegistroRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 64)
        private String password;

        @NotBlank @Size(max = 100)
        private String nombre;

        private Set<String> roles;
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String accessToken;
        private String idToken;
        private String refreshToken;
        private Integer expiresIn;
    }

    @Data
    public static class SolicitudPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class ConfirmarPasswordRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6, max = 6)
        private String codigo;

        @NotBlank @Size(min = 8, max = 64)
        private String nuevaContrasena;
    }
}