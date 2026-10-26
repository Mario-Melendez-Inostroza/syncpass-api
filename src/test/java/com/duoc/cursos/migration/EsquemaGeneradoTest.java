package com.duoc.cursos.migration;

import com.duoc.cursos.model.Asistencia;
import com.duoc.cursos.model.Asistente;
import com.duoc.cursos.model.Encuesta;
import com.duoc.cursos.model.Evento;
import com.duoc.cursos.model.Proyecto;
import com.duoc.cursos.model.Usuario;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genera en build/schema.sql el DDL de PostgreSQL que Hibernate espera para las entidades, SIN conectarse
 * a ninguna base de datos. Sirve de referencia para escribir y revisar las migraciones de Flyway y para
 * comparar contra el esquema real de Supabase (docs/MIGRACIONES.md): si las migraciones y este archivo
 * divergen, el arranque con ddl-auto=validate fallaría.
 *
 * No toca db/migration: solo escribe en build/.
 */
class EsquemaGeneradoTest {

    static final Path DESTINO = Path.of("build", "schema.sql");

    @Test
    @DisplayName("Genera build/schema.sql (PostgreSQL) desde las entidades, sin base de datos")
    void generaElEsquemaDePostgres() throws Exception {
        Files.createDirectories(DESTINO.getParent());
        Files.deleteIfExists(DESTINO);

        StandardServiceRegistry registro = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                // Sin esto Hibernate intentaría conectarse para averiguar la versión del servidor.
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                // Generación de scripts estándar de JPA: escribe el DDL en un archivo y no toca ninguna base.
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", DESTINO.toAbsolutePath().toString())
                .applySetting("hibernate.format_sql", "true")
                .build();
        try {
            Metadata metadata = new MetadataSources(registro)
                    .addAnnotatedClass(Usuario.class)
                    .addAnnotatedClass(Proyecto.class)
                    .addAnnotatedClass(Evento.class)
                    .addAnnotatedClass(Asistente.class)
                    .addAnnotatedClass(Asistencia.class)
                    .addAnnotatedClass(Encuesta.class)
                    .buildMetadata();

            // Construir la SessionFactory es lo que dispara la generación del script.
            try (SessionFactory ignorada = metadata.buildSessionFactory()) {
                // nada más que hacer
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registro);
        }

        String sql = Files.readString(DESTINO).toLowerCase();
        for (String tabla : new String[]{"usuarios", "usuario_roles", "proyectos", "eventos", "asistentes",
                "asistencias", "encuestas"}) {
            assertTrue(sql.contains("create table " + tabla), "falta la tabla " + tabla);
        }
    }
}
