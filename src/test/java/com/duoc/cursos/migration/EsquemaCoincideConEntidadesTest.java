package com.duoc.cursos.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las migraciones de Flyway producen un esquema que Hibernate acepta con ddl-auto=validate.
 *
 * Arranca la aplicación entera contra una H2 en modo PostgreSQL, deja que Flyway cree el esquema desde
 * cero y obliga a Hibernate a COMPROBAR (no crear) que tablas, columnas y tipos coinciden con las
 * entidades. Si una entidad cambia sin su migración, el contexto no arranca y esta prueba falla.
 *
 * Mismo límite que MigracionesFlywayTest: H2 no es PostgreSQL.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:validacion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class EsquemaCoincideConEntidadesTest {

    @Test
    @DisplayName("Flyway crea el esquema y Hibernate lo valida contra las entidades")
    void hibernateValidaElEsquemaDeFlyway() {
        // Llegar aquí significa que el contexto arrancó: Flyway migró y validate no encontró diferencias.
        assertTrue(true);
    }
}
