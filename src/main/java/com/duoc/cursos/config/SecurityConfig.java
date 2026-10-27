package com.duoc.cursos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Claim de Cognito con los grupos del usuario. Viaja en el access token y en el id token. */
    static final String CLAIM_GRUPOS = "cognito:groups";

    /** Prefijo que Spring exige para hasRole(...): hasRole("COORDINADOR") busca la authority ROLE_COORDINADOR. */
    static final String PREFIJO_ROL = "ROLE_";

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Sin sesión: cada petición lleva su JWT. Evita crear JSESSIONID.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**", "/api/health").permitAll()
                // Quórum en vivo: vista de supervisión del Docente / Coordinador(a) de Proyecto (caso §3.1). Requiere el grupo
                // COORDINADOR en el User Pool; si no existe, solo este endpoint responde 403.
                .requestMatchers(HttpMethod.GET, "/api/eventos/*/resumen").hasRole("COORDINADOR")
                // Gestión del catálogo y reporte de asistencia: solo el Docente / Coordinador(a) (caso §3.1, §2.4).
                .requestMatchers(HttpMethod.GET, "/api/eventos/*/asistencias", "/api/eventos/*/comentarios").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.GET, "/api/informes", "/api/asistentes").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.POST, "/api/asistentes").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.PUT, "/api/asistentes/*").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/asistentes/*").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.POST, "/api/proyectos", "/api/eventos").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.PUT, "/api/proyectos/*", "/api/eventos/*").hasRole("COORDINADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/proyectos/*", "/api/eventos/*").hasRole("COORDINADOR")
                // Cuenta del Asistente: inscribirse a talleres y ver su QR (caso §3.1).
                .requestMatchers("/api/mi/**").hasRole("ASISTENTE")
                // La sincronización y las notificaciones son del equipo (monitor o coordinador): el asistente no marca asistencias.
                .requestMatchers("/api/sync/**", "/api/notificar/**").hasAnyRole("MONITOR", "COORDINADOR")
                // Lo demás (p. ej. el catálogo de proyectos y eventos): basta con estar autenticado.
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .formLogin(form -> form.disable())
            .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    /**
     * Ignora la cabecera Authorization en las rutas públicas (permitAll).
     *
     * Con el resource server activo, un Bearer caducado o corrupto se rechaza con 401 incluso en rutas
     * permitAll(). Eso rompería el login, el registro y el refresh de un cliente que conserva un token
     * viejo (la web guarda el token en localStorage), y haría fallar un health check que lo envíe.
     * /api/auth/logout sigue leyendo la cabecera a mano en el controlador, así que no le afecta.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver porDefecto = new DefaultBearerTokenResolver();
        return request -> esRutaPublica(request.getRequestURI()) ? null : porDefecto.resolve(request);
    }

    /** Debe coincidir con las rutas permitAll() de filterChain. */
    private static boolean esRutaPublica(String uri) {
        return uri.startsWith("/api/auth/") || uri.equals("/api/health");
    }

    /**
     * Convierte cognito:groups en authorities ROLE_<GRUPO>. Sin el claim no concede ninguna authority
     * (no hay valores por defecto: no se inventan roles). Los grupos se normalizan a mayúsculas, de modo
     * que el grupo "coordinador" y "COORDINADOR" son el mismo rol.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> grupos = jwt.getClaimAsStringList(CLAIM_GRUPOS);
            if (grupos == null) {
                return List.<GrantedAuthority>of();
            }
            return grupos.stream()
                    .filter(g -> g != null && !g.isBlank())
                    .<GrantedAuthority>map(g -> new SimpleGrantedAuthority(PREFIJO_ROL + g.trim().toUpperCase(Locale.ROOT)))
                    .toList();
        });
        return converter;
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