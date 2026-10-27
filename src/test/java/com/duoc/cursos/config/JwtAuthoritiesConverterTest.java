package com.duoc.cursos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * cognito:groups -> authorities ROLE_*. Prueba unitaria del converter, sin Spring ni User Pool: lo que
 * se verifica es la regla de mapeo. Que los grupos EXISTAN en Cognito es cosa del despliegue.
 */
class JwtAuthoritiesConverterTest {

    private final JwtAuthenticationConverter converter =
            new SecurityConfig(new CorsProperties()).jwtAuthenticationConverter();

    private static Jwt jwtConGrupos(Object grupos) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("sub-de-prueba")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (grupos != null) {
            builder.claim("cognito:groups", grupos);
        }
        return builder.build();
    }

    /**
     * Solo los roles. Spring Security 7 añade por su cuenta una authority FACTOR_BEARER (registra con qué
     * mecanismo se autenticó el usuario): no es un rol y no la concede ningún grupo, así que no se cuenta.
     */
    private Set<String> autoridades(Jwt jwt) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwt);
        return token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Un grupo se convierte en ROLE_<GRUPO>: el prefijo ROLE_ es el que hasRole() busca")
    void grupo_seConvierteEnRole() {
        assertEquals(Set.of("ROLE_COORDINADOR"), autoridades(jwtConGrupos(List.of("COORDINADOR"))));
    }

    @Test
    @DisplayName("Varios grupos -> varias authorities")
    void variosGrupos() {
        assertEquals(Set.of("ROLE_COORDINADOR", "ROLE_MONITOR"),
                autoridades(jwtConGrupos(List.of("COORDINADOR", "MONITOR"))));
    }

    @Test
    @DisplayName("Los grupos se normalizan a mayúsculas: 'coordinador' y 'COORDINADOR' son el mismo rol")
    void normalizaAMayusculas() {
        assertEquals(Set.of("ROLE_COORDINADOR"), autoridades(jwtConGrupos(List.of("coordinador"))));
    }

    @Test
    @DisplayName("Sin el claim cognito:groups no se concede ninguna authority (no se inventan roles)")
    void sinClaim_sinAuthorities() {
        assertTrue(autoridades(jwtConGrupos(null)).isEmpty());
    }

    @Test
    @DisplayName("Una lista de grupos vacía no concede nada")
    void listaVacia() {
        assertTrue(autoridades(jwtConGrupos(List.of())).isEmpty());
    }

    @Test
    @DisplayName("Entradas vacías o en blanco se ignoran")
    void ignoraEntradasEnBlanco() {
        assertEquals(Set.of("ROLE_MONITOR"), autoridades(jwtConGrupos(List.of("", "  ", "MONITOR"))));
    }

    @Test
    @DisplayName("El principal sigue siendo el sub del token")
    void principalEsElSub() {
        JwtAuthenticationToken token = (JwtAuthenticationToken) converter.convert(jwtConGrupos(List.of("MONITOR")));
        assertEquals("sub-de-prueba", token.getName());
    }
}
