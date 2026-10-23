package com.duoc.cursos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Si CORS está deshabilitado, retornamos una configuración vacía (sin CORS)
        if (!corsProperties.isEnabled()) {
            configuration.setAllowedOrigins(List.of());
            configuration.setAllowedMethods(List.of());
            return new UrlBasedCorsConfigurationSource() {
                @Override
                public CorsConfiguration getCorsConfiguration(jakarta.servlet.http.HttpServletRequest request) {
                    return configuration;
                }
            };
        }

        // Configurar orígenes permitidos
        String allowedOrigins = corsProperties.getAllowedOrigins();
        if ("*".equals(allowedOrigins.trim())) {
            configuration.setAllowedOrigins(List.of("*"));
            // Cuando se usa "*", allowCredentials debe ser false por seguridad
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .toList()
            );
            configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        }

        // Configurar métodos permitidos
        configuration.setAllowedMethods(
            Arrays.stream(corsProperties.getAllowedMethods().split(","))
                .map(String::trim)
                .toList()
        );

        // Configurar headers permitidos
        String allowedHeaders = corsProperties.getAllowedHeaders();
        if ("*".equals(allowedHeaders.trim())) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(
                Arrays.stream(allowedHeaders.split(","))
                    .map(String::trim)
                    .toList()
            );
        }

        // Configurar headers expuestos
        configuration.setExposedHeaders(
            Arrays.stream(corsProperties.getExposedHeaders().split(","))
                .map(String::trim)
                .toList()
        );

        // Configurar max age para preflight
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}