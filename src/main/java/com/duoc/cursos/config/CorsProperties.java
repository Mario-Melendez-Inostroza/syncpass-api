package com.duoc.cursos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Habilita o deshabilita CORS completamente.
     * Cuando pasa por API Gateway, se puede deshabilitar poniendo este valor en false.
     */
    private boolean enabled = true;

    /**
     * Orígenes permitidos. Usa "*" para permitir todos.
     * Ejemplo: "https://app.midominio.com,https://otro.dominio.com"
     */
    private String allowedOrigins = "*";

    /**
     * Métodos HTTP permitidos
     */
    private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";

    /**
     * Headers permitidos
     */
    private String allowedHeaders = "*";

    /**
     * Headers expuestos al cliente
     */
    private String exposedHeaders = "Authorization,Content-Type";

    /**
     * Permitir credenciales (cookies, authorization headers)
     * Nota: Si allowedOrigins es "*", esto debe ser false por seguridad
     */
    private boolean allowCredentials = false;

    /**
     * Tiempo máximo de cache para preflight requests (en segundos)
     */
    private long maxAge = 3600;

    // Getters y Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(String allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public String getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(String allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public String getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(String exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }
}