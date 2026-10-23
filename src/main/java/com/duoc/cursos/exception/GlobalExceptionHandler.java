package com.duoc.cursos.exception;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduce las excepciones a respuestas HTTP con el formato {error, mensaje, status, detalles}.
 *
 * Regla: el cliente recibe mensajes propios y fijos; el detalle técnico de AWS solo va al log
 * (nunca se registran contraseñas ni tokens: el SDK no los incluye en sus excepciones).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String mensaje;
        private final int status;
        private final Map<String, String> detalles;

        public ErrorResponse(String error, String mensaje, int status) {
            this.error = error;
            this.mensaje = mensaje;
            this.status = status;
            this.detalles = new HashMap<>();
        }
    }

    // --- Excepciones específicas de Cognito ---

    @ExceptionHandler(UsernameExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameExists(UsernameExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", "El email ya está registrado", HttpStatus.CONFLICT.value()));
    }

    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthorized(NotAuthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "Credenciales inválidas", HttpStatus.UNAUTHORIZED.value()));
    }

    @ExceptionHandler(UserNotConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleUserNotConfirmed(UserNotConfirmedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("USER_NOT_CONFIRMED", "El usuario aún no está confirmado", HttpStatus.FORBIDDEN.value()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex) {
        log.warn("Cognito rechazó la contraseña por la política del pool");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", "La contraseña no cumple la política de contraseñas del servicio", HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(CodeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCodeMismatch(CodeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", "Código de verificación inválido o expirado", HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(ExpiredCodeException.class)
    public ResponseEntity<ErrorResponse> handleExpiredCode(ExpiredCodeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", "El código de verificación ha expirado", HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("TOO_MANY_REQUESTS", "Demasiadas solicitudes. Intente más tarde", HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @ExceptionHandler(LimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLimitExceeded(LimitExceededException ex) {
        log.warn("Cognito: límite excedido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse("TOO_MANY_REQUESTS", "Límite de intentos excedido. Intente más tarde", HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(InvalidParameterException ex) {
        log.warn("Cognito rechazó un parámetro: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", "Parámetro inválido", HttpStatus.BAD_REQUEST.value()));
    }

    /**
     * El User Pool, el app client o el grupo asignado al registrar (AuthService.ROL_POR_DEFECTO) no existen.
     * Es un problema de configuración del servidor, no del cliente.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCognitoResourceNotFound(ResourceNotFoundException ex) {
        log.error("Configuración de Cognito incompleta (¿existe el grupo '{}' en el User Pool?): {}",
                com.duoc.cursos.service.AuthService.ROL_POR_DEFECTO, ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("CONFIGURATION_ERROR",
                        "El servicio de autenticación no está configurado correctamente",
                        HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    /** Cognito respondió con un reto (p. ej. cambio de contraseña obligatorio) que este endpoint no implementa. */
    @ExceptionHandler(AuthChallengeException.class)
    public ResponseEntity<ErrorResponse> handleAuthChallenge(AuthChallengeException ex) {
        log.warn("Login con reto de autenticación no soportado: {}", ex.getChallengeName());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("CHALLENGE_NOT_SUPPORTED",
                        "La cuenta requiere un paso adicional de autenticación que no está soportado",
                        HttpStatus.FORBIDDEN.value()));
    }

    /**
     * Resto de errores de Cognito: principalmente permisos IAM insuficientes del backend
     * (AccessDeniedException) u otros fallos del servicio. Los más específicos se resuelven antes.
     */
    @ExceptionHandler(CognitoIdentityProviderException.class)
    public ResponseEntity<ErrorResponse> handleCognitoError(CognitoIdentityProviderException ex) {
        String code = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : null;
        if ("AccessDeniedException".equals(code) || "UnauthorizedException".equals(code)) {
            log.error("Cognito denegó la operación al backend (revisar permisos IAM del rol/credenciales): {}",
                    ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("COGNITO_PERMISSION_DENIED",
                            "El servicio de autenticación no tiene permisos suficientes para completar la operación",
                            HttpStatus.BAD_GATEWAY.value()));
        }
        log.error("Error inesperado de Cognito (código={}, http={}): {}", code, ex.statusCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("COGNITO_ERROR",
                        "El servicio de autenticación no pudo procesar la solicitud",
                        HttpStatus.BAD_GATEWAY.value()));
    }

    /** Sin credenciales AWS, sin red hacia Cognito, región incorrecta, etc. */
    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<ErrorResponse> handleSdkClient(SdkClientException ex) {
        log.error("No se pudo contactar con Cognito (credenciales, región o red): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("SERVICE_UNAVAILABLE",
                        "No se pudo contactar con el servicio de autenticación",
                        HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    // --- Persistencia ---

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad en la base de datos: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", "El email ya está registrado", HttpStatus.CONFLICT.value()));
    }

    // --- Validación de DTOs y JSON ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Datos de entrada inválidos", HttpStatus.BAD_REQUEST.value());
        ex.getBindingResult().getFieldErrors().forEach(err ->
                error.getDetalles().put(err.getField(), err.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", "El cuerpo de la petición no es un JSON válido", HttpStatus.BAD_REQUEST.value()));
    }

    // --- Genérica ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Errores HTTP estándar de Spring MVC (405, 415, 404...): conservar su código en vez de un 500.
        if (ex instanceof org.springframework.web.ErrorResponse standard) {
            HttpStatus status = HttpStatus.resolve(standard.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return ResponseEntity.status(status)
                        .body(new ErrorResponse(status.name(), status.getReasonPhrase(), status.value()));
            }
        }
        log.error("Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
