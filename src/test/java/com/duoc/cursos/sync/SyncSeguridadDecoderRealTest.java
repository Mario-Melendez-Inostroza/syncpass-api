package com.duoc.cursos.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Igual que SyncSeguridadTest pero con el JwtDecoder REAL de Spring (NimbusJwtDecoder), sin dobles.
 *
 * Un token que ni siquiera es un JWT se rechaza al parsearlo, ANTES de pedir las claves públicas, así que
 * estas pruebas no necesitan red ni User Pool. No pueden cubrir un token bien formado con firma inválida:
 * para eso haría falta servir un JWKS, y esa regla la cubre SyncSeguridadTest con un doble.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SyncSeguridadDecoderRealTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Sin cabecera Authorization -> 401")
    void sinToken() throws Exception {
        mvc.perform(get("/api/sync/catalogo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un Bearer que no es un JWT (p. ej. la cadena 'undefined' que guarda la web) -> 401")
    void bearerQueNoEsJwt() throws Exception {
        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Bearer undefined"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un esquema de autenticación distinto de Bearer -> 401")
    void esquemaIncorrecto() throws Exception {
        mvc.perform(get("/api/sync/catalogo").header("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ=="))
                .andExpect(status().isUnauthorized());
    }
}
