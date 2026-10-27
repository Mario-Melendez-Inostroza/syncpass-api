# Migraciones de base de datos (Flyway)

Hasta ahora el esquema lo creaba Hibernate con `ddl-auto: update`. Desde la Fase 2 manda **Flyway**
(`src/main/resources/db/migration`) y Hibernate ya no toca el esquema (`ddl-auto: none`).

| Archivo | Contenido |
|---|---|
| `V1__baseline_usuarios.sql` | `usuarios` y `usuario_roles`: lo que ya existía, descrito tal como Hibernate lo espera |
| `V2__esquema_syncpass.sql` | `proyectos`, `eventos`, `asistentes`, `asistencias`, `encuestas` |
| `V3__asistentes_uuid.sql` | `asistentes.uuid` (nullable, único): clave de idempotencia del alta de asistentes en terreno desde el móvil |
| `V4__eventos_quorum_minimo.sql` | `eventos.quorum_minimo` (opcional, `>= 1`): mínimo de asistentes para dar por cumplido el quórum |
| `db/demo/R__datos_demo.sql` | Datos ficticios. **Solo con el perfil `demo`**, nunca por defecto |

## Estado de la verificación

**Comprobado** (pruebas automáticas, `./gradlew.bat test`):

- Las migraciones se ejecutan sobre una base vacía y Hibernate las acepta con `ddl-auto=validate`
  (`EsquemaCoincideConEntidadesTest`). Se verificó que la prueba falla si se renombra una columna.
- Una base con las tablas de `V1` y **sin** historial de Flyway se baselinea en la versión 1 y ejecuta solo `V2`
  (`MigracionesFlywayTest`).
- Los datos demo no se cargan sin el perfil `demo`, y cargarlos dos veces no duplica filas.

**NO comprobado**: nada se ha ejecutado contra un PostgreSQL real ni contra Supabase. Las pruebas usan H2 en
modo de compatibilidad PostgreSQL, que detecta nombres, tipos y errores de lógica pero **no** una sintaxis que
solo PostgreSQL rechace. Antes de aplicar en Supabase, ejecutar las migraciones en un PostgreSQL desechable
(p. ej. un contenedor `postgres:16`) y arrancar la aplicación con `ddl-auto=validate`.

## Paso 0 (obligatorio antes de tocar Supabase): inspeccionar el esquema real

`baseline-on-migrate` **marca** la base como baselineada; **no comprueba** que su contenido coincida con `V1`.
Si la tabla real difiere en un tipo o una longitud, Flyway lo da por bueno y el fallo aparece más tarde,
al arrancar con `ddl-auto=validate`, es decir, en el despliegue. Por eso, primero se mira.

Consultas de **solo lectura** (no modifican nada):

```sql
-- ¿Flyway ya dejó historial? Devuelve NULL la primera vez.
select to_regclass('public.flyway_schema_history');

-- Forma real de las tablas de autenticación.
select table_name, column_name, data_type, character_maximum_length, is_nullable
from information_schema.columns
where table_schema = 'public' and table_name in ('usuarios', 'usuario_roles')
order by table_name, ordinal_position;

-- Restricciones reales.
select conrelid::regclass as tabla, conname, contype, pg_get_constraintdef(oid) as definicion
from pg_constraint
where connamespace = 'public'::regnamespace
  and conrelid::regclass::text in ('usuarios', 'usuario_roles');
```

Comparar con `V1` y con el DDL que Hibernate espera. Ese DDL se genera sin conectarse a ninguna base:

```
./gradlew.bat test --tests "com.duoc.cursos.migration.EsquemaGeneradoTest"
```

y queda en `build/schema.sql`. Debe coincidir con lo que devuelven las consultas: `usuarios(id varchar(64),
email varchar(255) único, nombre varchar(255))` y `usuario_roles(usuario_id varchar(64), rol varchar(255))`
con clave primaria compuesta.

- **Si coincide**: `V1` es fiel y el baseline es seguro.
- **Si difiere**: resolver la diferencia **antes** de migrar (corregir la base o añadir una migración
  que la alinee). No arrancar la aplicación hasta entonces.

## Qué hace Flyway según el estado de la base

Configuración (`application.yml`): `baseline-on-migrate: true`, `baseline-version: "1"`.

| Estado de la base | Resultado |
|---|---|
| Vacía, sin historial | **No** baselinea. Ejecuta `V1` y `V2` |
| Con `usuarios` ya creada, sin historial | Baselinea en `V1` (no la ejecuta) y ejecuta `V2` y `V3` |
| Con historial de Flyway | Flujo normal: ejecuta lo pendiente |
| Con tablas, sin historial y **sin** `baseline-on-migrate` | Se niega a actuar (falla), no toca nada |

## `ddl-auto`

Está en `none`: Flyway es la única autoridad sobre el esquema. Pasar a `validate` **solo cuando el diff del
Paso 0 cuadre**, porque `validate` convierte cualquier diferencia cosmética en un fallo de arranque que
conviene descubrir antes de llegar al EC2.

## Puerto de Supabase

Usar el **Session pooler (puerto 5432)** o la conexión directa, que es lo que usa hoy `DATABASE_URL`. El
*pooler* en **modo transacción (puerto 6543)** tiene limitaciones conocidas con las herramientas de migración
(no admite sentencias preparadas ni estado de sesión, y Flyway toma bloqueos al migrar). No se ha probado
Flyway contra ese modo: si alguien cambia el puerto a 6543, hay que verificar que las migraciones arrancan antes
de desplegar.

## Reglas para el futuro

- **No editar** una migración ya aplicada en una base real (cambiaría su checksum y `validate` fallaría).
  Cualquier cambio de esquema es una migración nueva (`V3__...`).
- Si se modifica una entidad, `EsquemaCoincideConEntidadesTest` falla hasta que exista su migración.
- Las migraciones versionadas **no** contienen datos de demostración (hay una prueba que lo comprueba).

## Datos de demostración

El catálogo ficticio (un proyecto, dos eventos, cinco asistentes con prefijo `DEMO`) sirve para probar la
sincronización y el quórum. Se carga **solo** con:

```
SPRING_PROFILES_ACTIVE=demo
```

`application-demo.yml` añade `classpath:db/demo` a las ubicaciones de Flyway; `application.yml` y
`application-prod.yml` no la mencionan (una prueba lo verifica), de modo que un arranque normal o de producción
nunca los inserta. **No activar `demo` junto a `prod`** (`prod,demo` cargaría datos ficticios en producción).

Para retirarlos de una base donde se hayan cargado, borrar las filas cuyo `codigo` empiece por `DEMO-` y los
eventos y el proyecto cuyo nombre empiece por `DEMO`.

## Recomendación pendiente de verificar: Row Level Security en Supabase

Supabase expone por defecto las tablas del esquema `public` a través de su API de datos (PostgREST). Una
tabla creada por SQL **sin** Row Level Security queda accesible con la clave `anon`, que es pública. Esto ya
aplica a `usuarios` y ahora también a las tablas nuevas.

La aplicación no usa esa API (se conecta por JDBC), así que lo seguro es activar RLS **sin políticas** en todas
las tablas: el rol que usa el backend es el propietario de las tablas y no está sujeto a RLS, y la API de datos
queda cerrada.

```sql
alter table usuarios       enable row level security;
alter table usuario_roles  enable row level security;
alter table proyectos      enable row level security;
alter table eventos        enable row level security;
alter table asistentes     enable row level security;
alter table asistencias    enable row level security;
alter table encuestas      enable row level security;
```

**No se incluyó como migración a propósito**: depende de que el usuario de `DATABASE_USER` sea propietario
de las tablas (o tenga `BYPASSRLS`), y eso no se pudo verificar desde aquí. Si no lo fuera, activar RLS sin
políticas bloquearía **todo** el acceso del backend. Comprobarlo primero en Supabase y aplicarlo a mano.

## V3 en la base de producción (Supabase)

Supabase ya está en V2 (con historial de Flyway). Al arrancar la versión nueva del servicio, Flyway aplica **solo V3**
(`alter table asistentes add column uuid` + restricción única): no toca datos, y los asistentes existentes quedan con
`uuid` NULL. No requiere pasos manuales. En el log debe aparecer `Successfully applied 1 migration`.

## V4 en la base de producción (Supabase)

Supabase ya está en V3. Al arrancar la versión nueva, Flyway aplica **solo V4** (`alter table eventos add column
quorum_minimo` + una restricción `>= 1`): no toca datos, y los eventos existentes quedan sin mínimo (`NULL`). En el log
debe aparecer `Successfully applied 1 migration`.
