package com.duoc.cursos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprueba la configuración de base de datos del perfil "prod" sin conectarse a ninguna base:
 * enlaza los YAML reales (application.yml principal + application-prod.yml) contra un entorno simulado
 * y aislado, que no ve las variables reales de la máquina. Los valores son ficticios.
 */
class ProdProfileConfigTest {

    private static final String URL_FICTICIA = "jdbc:postgresql://host.invalido:5432/base_de_prueba?sslmode=require";

    /** Entorno simulado: variables ficticias (máxima precedencia) + application-prod.yml + application.yml. */
    private static Binder binder(Map<String, String> variables) throws IOException {
        MockEnvironment entorno = new MockEnvironment();
        variables.forEach(entorno::setProperty);

        YamlPropertySourceLoader cargador = new YamlPropertySourceLoader();
        for (PropertySource<?> fuente : cargador.load("application-prod", new ClassPathResource("application-prod.yml"))) {
            entorno.getPropertySources().addLast(fuente);
        }
        for (PropertySource<?> fuente : cargador.load("application", ymlPrincipal())) {
            entorno.getPropertySources().addLast(fuente);
        }
        return Binder.get(entorno);
    }

    /** src/test/resources/application.yml tapa al principal en el classpath: se busca el de main explícitamente. */
    private static Resource ymlPrincipal() throws IOException {
        for (Resource recurso : new PathMatchingResourcePatternResolver().getResources("classpath*:application.yml")) {
            if (!recurso.getURL().getPath().contains("/test/")) {
                return recurso;
            }
        }
        throw new IllegalStateException("No se encontró el application.yml principal en el classpath");
    }

    private static Map<String, String> variablesDeBase() {
        return Map.of(
                "DATABASE_URL", URL_FICTICIA,
                "DATABASE_USER", "usuario_ficticio",
                "DATABASE_PASSWORD", "clave-ficticia");
    }

    @Test
    @DisplayName("prod: show-sql desactivado")
    void prod_desactivaShowSql() throws IOException {
        assertFalse(binder(variablesDeBase()).bind("spring.jpa.show-sql", Boolean.class).get());
    }

    @Test
    @DisplayName("prod: url, usuario y contraseña salen del entorno, no del código")
    void prod_credencialesSalenDelEntorno() throws IOException {
        Binder binder = binder(variablesDeBase());

        assertEquals(URL_FICTICIA, binder.bind("spring.datasource.url", String.class).get());
        assertEquals("usuario_ficticio", binder.bind("spring.datasource.username", String.class).get());
        assertEquals("clave-ficticia", binder.bind("spring.datasource.password", String.class).get());
    }

    /*
     * Spring deja sin resolver los marcadores ${...} cuando falta la variable (no lanza una excepción al enlazar).
     * Lo que se comprueba es que NO exista ningún valor por defecto: el valor queda como el marcador literal,
     * que el driver JDBC rechaza al arrancar ("claims to not accept jdbcUrl, ${DATABASE_URL}"), en lugar de caer
     * a localhost o a postgres/postgres. El arranque fallido se verificó manualmente con el JAR (código de salida 1).
     */

    @Test
    @DisplayName("prod: sin DATABASE_URL no hay valor por defecto (no cae a localhost)")
    void prod_sinUrl_noTieneValorPorDefecto() throws IOException {
        Binder binder = binder(Map.of("DATABASE_USER", "u", "DATABASE_PASSWORD", "p"));

        assertEquals("${DATABASE_URL}", binder.bind("spring.datasource.url", String.class).get());
    }

    @Test
    @DisplayName("prod: sin DATABASE_USER ni DATABASE_PASSWORD no hay valor por defecto (no usa postgres/postgres)")
    void prod_sinUsuarioNiClave_noTieneValorPorDefecto() throws IOException {
        Binder binder = binder(Map.of("DATABASE_URL", URL_FICTICIA));

        assertEquals("${DATABASE_USER}", binder.bind("spring.datasource.username", String.class).get());
        assertEquals("${DATABASE_PASSWORD}", binder.bind("spring.datasource.password", String.class).get());
    }

    @Test
    @DisplayName("prod: exige SSL como respaldo cuando la URL no trae sslmode")
    void prod_exigeSslComoRespaldo() throws IOException {
        Map<String, String> propiedades = binder(variablesDeBase())
                .bind("spring.datasource.hikari.data-source-properties", Bindable.mapOf(String.class, String.class))
                .get();

        assertEquals("require", propiedades.get("sslmode"));
    }

    @Test
    @DisplayName("Pool: 5 conexiones máximo por defecto y 1 en reposo")
    void pool_valoresPorDefecto() throws IOException {
        Binder binder = binder(variablesDeBase());

        assertEquals(5, binder.bind("spring.datasource.hikari.maximum-pool-size", Integer.class).get());
        assertEquals(1, binder.bind("spring.datasource.hikari.minimum-idle", Integer.class).get());
    }

    @Test
    @DisplayName("Pool: DATABASE_POOL_MAX_SIZE permite ajustar el máximo")
    void pool_seAjustaConVariableDeEntorno() throws IOException {
        Map<String, String> variables = new java.util.HashMap<>(variablesDeBase());
        variables.put("DATABASE_POOL_MAX_SIZE", "3");

        assertEquals(3, binder(variables).bind("spring.datasource.hikari.maximum-pool-size", Integer.class).get());
    }
}
