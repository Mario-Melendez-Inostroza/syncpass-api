package com.duoc.cursos.controller;

import com.duoc.cursos.common.dto.ApiResponse;
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

/**
 * Todas las respuestas correctas van envueltas en ApiResponse{success, data, message, timestamp},
 * que es el contrato que ya consume el cliente móvil (ApiResponse.kt / LoginResponse.kt).
 * Los errores los produce GlobalExceptionHandler con su propio ErrorResponse{error, mensaje, status, detalles}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<ApiResponse<Usuario>> registrar(@Valid @RequestBody RegistroRequest req) {
        // El rol lo asigna el servidor (AuthService.ROL_POR_DEFECTO); req.getRoles() se ignora a propósito.
        Usuario usuario = authService.registrar(req.getEmail(), req.getPassword(), req.getNombre());
        return ResponseEntity.ok(ApiResponse.ok(usuario, "Usuario registrado exitosamente"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthenticationResultType result = authService.login(req.getEmail(), req.getPassword());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.accessToken());
        response.setIdToken(result.idToken());
        response.setRefreshToken(result.refreshToken());
        response.setExpiresIn(result.expiresIn());

        return ResponseEntity.ok(ApiResponse.ok(response, "Login exitoso"));
    }

    /**
     * Renovación real vía REFRESH_TOKEN_AUTH de Cognito.
     *
     * Cognito no rota el refresh token en este flujo (devuelve null), así que se reenvía el que mandó
     * el cliente para que no lo pierda al guardar la respuesta: el móvil hace saveTokens() con los tres
     * valores y un null le borraría el refresh token guardado. No se inventa ningún token: accessToken
     * e idToken son siempre los que devuelve Cognito.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        AuthenticationResultType result = authService.refrescarTokens(req.getRefreshToken(), req.getEmail());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.accessToken());
        response.setIdToken(result.idToken());
        response.setRefreshToken(result.refreshToken() != null ? result.refreshToken() : req.getRefreshToken());
        response.setExpiresIn(result.expiresIn());

        return ResponseEntity.ok(ApiResponse.ok(response, "Token renovado"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Token de autorización requerido"));
        }
        String accessToken = bearerToken.substring(7);
        authService.logout(accessToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logout exitoso"));
    }

    @PostMapping("/olvide-contrasena/solicitar")
    public ResponseEntity<ApiResponse<Void>> solicitarPassword(@Valid @RequestBody SolicitudPasswordRequest req) {
        authService.solicitarRecuperacionContrasena(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null, "Código de recuperación enviado al email"));
    }

    @PostMapping("/olvide-contrasena/confirmar")
    public ResponseEntity<ApiResponse<Void>> confirmarPassword(@Valid @RequestBody ConfirmarPasswordRequest req) {
        authService.confirmarRecuperacionContrasena(req.getEmail(), req.getCodigo(), req.getNuevaContrasena());
        return ResponseEntity.ok(ApiResponse.ok(null, "Contraseña actualizada exitosamente"));
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

        /**
         * Se conserva solo por compatibilidad con clientes existentes (p. ej. la web envía "roles").
         * IGNORADO: el servidor asigna el rol; el cliente no puede solicitar privilegios.
         */
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
    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;

        /**
         * Obligatorio solo si el App Client de Cognito tiene client secret: en REFRESH_TOKEN_AUTH hay que
         * enviar SECRET_HASH, que se calcula con el username, y el refresh token es opaco (no es un JWT),
         * así que el username no se puede deducir de él. Si falta en esa configuración, el servicio lanza
         * RefreshUsernameRequiredException y se responde 400 con una explicación, no un 500 opaco.
         */
        @Email
        private String email;
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
