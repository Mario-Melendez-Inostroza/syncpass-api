package com.duoc.cursos.sync;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

/** Fabrica JWT ficticios con la forma de un access token de Cognito. No hay firma ni claves reales. */
final class TestJwts {

    private TestJwts() {
    }

    /**
     * @param sub    claim "sub"
     * @param grupos valores de "cognito:groups"; sin ninguno, el claim no se incluye (como en Cognito
     *               cuando el usuario no pertenece a ningún grupo)
     */
    static Jwt accessToken(String sub, String... grupos) {
        Instant ahora = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token-" + sub)
                .header("alg", "none")
                .subject(sub)
                .claim("token_use", "access")
                .claim("client_id", "client-de-prueba")
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(3600));
        if (grupos.length > 0) {
            builder.claim("cognito:groups", List.of(grupos));
        }
        return builder.build();
    }
}
