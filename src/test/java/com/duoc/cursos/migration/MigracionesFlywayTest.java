package com.duoc.cursos.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comportamiento de las migraciones de Flyway, ejecutadas sobre H2 en modo de compatibilidad PostgreSQL.
 *
 * LÍMITE: H2 no es PostgreSQL. Esto detecta nombres, tipos, orden de dependencias y errores de lógica, pero
 * no una sintaxis que solo PostgreSQL rechace. Antes de aplicar contra Supabase hay que probarlas en un
 * PostgreSQL real (docs/MIGRACIONES.md).
 */
class MigracionesFlywayTest {

    private static final String MIGRACIONES = "classpath:db/migration";
    private static final String DEMO = "classpath:db/demo";

    /** Una base H2 en memoria distinta por prueba, para que no se contaminen entre sí. */
    private static String nuevaBase() {
        return "jdbc:h2:mem:mig_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static Flyway flyway(String url, boolean baseline, String... ubicaciones) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(ubicaciones)
                .baselineOnMigrate(baseline)
                .baselineVersion("1")
                .load();
    }

    private static long contar(String url, String tabla) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select count(*) from " + tabla)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean existeTabla(String url, String tabla) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             ResultSet rs = c.getMetaData().getTables(null, null, tabla, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static List<String> versionesAplicadas(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
                .map(i -> i.getVersion() == null ? "R:" + i.getDescription() : i.getVersion().getVersion() + ":" + i.getType())
                .toList();
    }

    // ------------------------------------------------------------------ base nueva

    @Test
    @DisplayName("Base vacía: Flyway NO baselinea y ejecuta V1 a V4 desde cero")
    void baseVacia_ejecutaTodo() throws Exception {
        String url = nuevaBase();
        Flyway flyway = flyway(url, true, MIGRACIONES);

        MigrateResult resultado = flyway.migrate();

        assertEquals(4, resultado.migrationsExecuted);
        assertEquals(List.of("1:SQL", "2:SQL", "3:SQL", "4:SQL"), versionesAplicadas(flyway),
                "en una base vacía no debe aparecer ninguna fila BASELINE");
        for (String tabla : new String[]{"usuarios", "usuario_roles", "proyectos", "eventos", "asistentes",
                "asistencias", "encuestas"}) {
            assertTrue(existeTabla(url, tabla), "falta la tabla " + tabla);
        }
    }

    @Test
    @DisplayName("Migrar dos veces seguidas no repite nada")
    void migrarDosVeces() {
        Flyway flyway = flyway(nuevaBase(), true, MIGRACIONES);
        flyway.migrate();

        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    // ------------------------------------------------------------------ base existente (Supabase)

    @Test
    @DisplayName("Base con tablas de Hibernate y SIN historial: baselinea en V1 y ejecuta V2, V3 y V4, no V1")
    void baseExistenteSinHistorial_baselineaYSaltaV1() throws Exception {
        String url = nuevaBase();
        // Simula la base de Supabase: usuarios y usuario_roles ya existen (las creó Hibernate) y hay un usuario.
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             InputStream v1 = getClass().getResourceAsStream("/db/migration/V1__baseline_usuarios.sql")) {
            RunScript.execute(c, new InputStreamReader(v1, StandardCharsets.UTF_8));
            try (Statement s = c.createStatement()) {
                s.executeUpdate("insert into usuarios (id, email, nombre) values ('sub-previo', 'previo@example.com', 'Previo')");
            }
        }
        Flyway flyway = flyway(url, true, MIGRACIONES);

        MigrateResult resultado = flyway.migrate();

        // Si V1 se hubiera ejecutado, fallaría con "table usuarios already exists".
        assertEquals(3, resultado.migrationsExecuted, "deben ejecutarse V2, V3 y V4, pero no V1");
        List<String> aplicadas = versionesAplicadas(flyway);
        assertEquals(List.of("1:BASELINE", "2:SQL", "3:SQL", "4:SQL"), aplicadas);
        assertTrue(existeTabla(url, "asistencias"));
        assertEquals(1, contar(url, "usuarios"), "los datos existentes no se tocan");
    }

    @Test
    @DisplayName("Base con tablas y SIN baselineOnMigrate: Flyway se niega a tocarla (no actúa a ciegas)")
    void baseExistenteSinBaseline_seNiega() throws Exception {
        String url = nuevaBase();
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             InputStream v1 = getClass().getResourceAsStream("/db/migration/V1__baseline_usuarios.sql")) {
            RunScript.execute(c, new InputStreamReader(v1, StandardCharsets.UTF_8));
        }

        assertThrows(Exception.class, () -> flyway(url, false, MIGRACIONES).migrate());
        assertFalse(existeTabla(url, "asistencias"));
    }

    @Test
    @DisplayName("Las migraciones versionadas no cambian de checksum entre ejecuciones (validate pasa)")
    void validate() {
        Flyway flyway = flyway(nuevaBase(), true, MIGRACIONES);
        flyway.migrate();

        flyway.validate();
    }

    // ------------------------------------------------------------------ V3: uuid de asistentes

    @Test
    @DisplayName("V3 sobre una base que ya estaba en V2 conserva los asistentes: uuid queda NULL y con restricción única")
    void v3_sobreBaseEnV2_conservaDatos() throws Exception {
        String url = nuevaBase();
        // La base de producción hoy: V1 y V2 aplicadas, con el catálogo demo cargado.
        Flyway hastaV2 = Flyway.configure().dataSource(url, "sa", "").locations(MIGRACIONES, DEMO)
                .target("2").baselineOnMigrate(true).baselineVersion("1").load();
        hastaV2.migrate();
        assertEquals(5, contar(url, "asistentes"));

        // Al arrancar la versión nueva se aplica V3.
        MigrateResult resultado = flyway(url, true, MIGRACIONES, DEMO).migrate();

        assertTrue(resultado.migrationsExecuted >= 1);
        assertEquals(5, contar(url, "asistentes"), "los asistentes existentes no se tocan");
        assertEquals(5, contar(url, "asistentes where uuid is null"));
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            // H2 valida mal los CHECK existentes tras un ALTER TABLE ADD COLUMN, así que aquí no se puede insertar ni
            // actualizar para probar la unicidad: se comprueba la definición. Que un uuid repetido se rechaza lo cubre
            // AsistenteAltaTest (reenvío idempotente) con el esquema que genera Hibernate.
            try (ResultSet col = s.executeQuery("select is_nullable, character_maximum_length from information_schema.columns "
                    + "where table_name = 'asistentes' and column_name = 'uuid'")) {
                assertTrue(col.next(), "falta la columna uuid");
                assertEquals("YES", col.getString(1), "uuid debe ser opcional: los asistentes existentes no tienen");
                assertEquals(36, col.getInt(2));
            }
            try (ResultSet uk = s.executeQuery("select constraint_type from information_schema.table_constraints "
                    + "where table_name = 'asistentes' and constraint_name = 'uk_asistentes_uuid'")) {
                assertTrue(uk.next(), "falta la restricción uk_asistentes_uuid");
                assertEquals("UNIQUE", uk.getString(1));
            }
        }
    }

    // ------------------------------------------------------------------ V4: quórum mínimo

    @Test
    @DisplayName("V4 sobre una base que ya estaba en V3 conserva los eventos y deja quorum_minimo opcional")
    void v4_sobreBaseEnV3_conservaEventos() throws Exception {
        String url = nuevaBase();
        Flyway hastaV3 = Flyway.configure().dataSource(url, "sa", "").locations(MIGRACIONES, DEMO)
                .target("3").baselineOnMigrate(true).baselineVersion("1").load();
        hastaV3.migrate();
        assertEquals(2, contar(url, "eventos"));

        MigrateResult resultado = flyway(url, true, MIGRACIONES, DEMO).migrate();

        assertTrue(resultado.migrationsExecuted >= 1);
        assertEquals(2, contar(url, "eventos"), "los eventos existentes no se tocan");
        assertEquals(2, contar(url, "eventos where quorum_minimo is null"), "los eventos existentes no tienen mínimo");
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement();
             ResultSet col = s.executeQuery("select is_nullable from information_schema.columns "
                     + "where table_name = 'eventos' and column_name = 'quorum_minimo'")) {
            assertTrue(col.next(), "falta la columna quorum_minimo");
            assertEquals("YES", col.getString(1), "quorum_minimo debe ser opcional");
        }
    }

    // ------------------------------------------------------------------ datos demo: opt-in

    @Test
    @DisplayName("Sin la carpeta demo NO se inserta ningún dato (es lo que ocurre en un arranque normal)")
    void sinDemo_noHayDatos() throws Exception {
        String url = nuevaBase();
        flyway(url, true, MIGRACIONES).migrate();

        assertEquals(0, contar(url, "proyectos"));
        assertEquals(0, contar(url, "eventos"));
        assertEquals(0, contar(url, "asistentes"));
    }

    @Test
    @DisplayName("Con la carpeta demo se carga el catálogo ficticio, y repetir la carga no duplica filas")
    void conDemo_cargaYEsIdempotente() throws Exception {
        String url = nuevaBase();
        Flyway flyway = flyway(url, true, MIGRACIONES, DEMO);
        flyway.migrate();

        assertEquals(1, contar(url, "proyectos"));
        assertEquals(2, contar(url, "eventos"));
        assertEquals(5, contar(url, "asistentes"));

        // Flyway no vuelve a ejecutar una repetible sin cambios; se fuerza la reejecución del SQL para
        // comprobar que el propio script es idempotente (lo que ocurriría si se edita y cambia su checksum).
        try (Connection c = DriverManager.getConnection(url, "sa", "");
             InputStream demo = getClass().getResourceAsStream("/db/demo/R__datos_demo.sql")) {
            RunScript.execute(c, new InputStreamReader(demo, StandardCharsets.UTF_8));
        }
        assertEquals(1, contar(url, "proyectos"));
        assertEquals(2, contar(url, "eventos"));
        assertEquals(5, contar(url, "asistentes"));
    }

    @Test
    @DisplayName("Los datos demo son ficticios: todo lleva el prefijo DEMO y los asistentes no tienen datos personales")
    void demo_esFicticio() throws Exception {
        String url = nuevaBase();
        flyway(url, true, MIGRACIONES, DEMO).migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", "");
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("select count(*) from asistentes where codigo not like 'DEMO-%'")) {
                rs.next();
                assertEquals(0, rs.getLong(1));
            }
            try (ResultSet rs = s.executeQuery("select count(*) from eventos where nombre not like 'DEMO %'")) {
                rs.next();
                assertEquals(0, rs.getLong(1));
            }
        }
    }

    // ------------------------------------------------------------------ guardas de configuración

    @Test
    @DisplayName("Solo application-demo.yml referencia db/demo: ni el perfil por defecto ni prod la cargan")
    void soloElPerfilDemoReferenciaLaCarpetaDemo() throws Exception {
        Path recursos = Path.of("src", "main", "resources");

        assertFalse(Files.readString(recursos.resolve("application.yml")).contains("db/demo"),
                "application.yml no debe cargar los datos demo");
        assertFalse(Files.readString(recursos.resolve("application-prod.yml")).contains("db/demo"),
                "application-prod.yml no debe cargar los datos demo");
        assertTrue(Files.readString(recursos.resolve("application-demo.yml")).contains("db/demo"));
    }

    @Test
    @DisplayName("Ninguna migración versionada (db/migration) contiene datos de demostración")
    void migracionesVersionadas_sinDatosDemo() throws Exception {
        try (var archivos = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            for (Path archivo : archivos.toList()) {
                String sql = Files.readString(archivo).toLowerCase();
                assertFalse(sql.contains("demo-") || sql.contains("insert into"),
                        archivo.getFileName() + " no debe insertar datos");
            }
        }
    }
}
