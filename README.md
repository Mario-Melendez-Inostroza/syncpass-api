# SyncPass · API (backend)

API REST de **SyncPass**, la plataforma de gestión de proyectos comunitarios y registro de asistencia en terreno del
caso **DSY1105 · Desarrollo de Aplicaciones Móviles** (Duoc UC, CITT Plaza Vespucio). Es el único punto de entrada de
la app Android y del panel web: ninguno habla directamente con Cognito ni con la base de datos.

Java 21 · Spring Boot 4.1 · Spring Security (resource server JWT) · Spring Data JPA · Flyway · PostgreSQL (Supabase) ·
Amazon Cognito. Todos los datos de demostración son **ficticios** (caso §5.1).

## El sistema completo

```
Usuario ─► Panel web (React)  ─┐
                                ├─► API Gateway ─► Esta API (Spring Boot, EC2) ─┬─► Amazon Cognito
Usuario ─► App Android (Kotlin)┘      (authorizer JWT)                          └─► Supabase PostgreSQL
```

| Repositorio | Qué es |
|---|---|
| **syncpass-api** (este) | Backend: autenticación, catálogo, sincronización por lotes, quórum y reportes |
| **syncpass-mobile** | App Android offline-first para el monitor (QR, lista, alta en terreno, encuestas) |
| **syncpass-web** | Página de inicio pública y panel del coordinador (proyectos, eventos, quórum, CSV) |

## El caso que resolvemos

**SyncPass** nace del caso de Ticdgm (CITT Plaza Vespucio, DSY1105): los proyectos de vinculación comunitaria toman la
asistencia **en papel** y consolidan todo a mano. Eso produce hojas perdidas, filas de 15 a 20 minutos en la entrada,
encuestas con menos de 15 % de respuesta (se envían semanas después) y barrios con poca señal donde una web normal no sirve.

La solución tiene tres piezas. **Este backend es el cerebro:** recibe lo que registran los teléfonos (aunque lo envíen horas
después), lo guarda en PostgreSQL, decide **quién puede hacer qué** y calcula quórum e informes para el coordinador.

Metas del caso: registrar en **menos de 5 segundos**, **más de 65 %** de respuesta a la encuesta, **80 %** menos tiempo de
consolidación y **100 %** de operación sin conexión. Son objetivos, no mediciones.

## Cómo usamos la IA

Trabajamos con **Claude Code** (Anthropic) como asistente de programación, y lo hacemos como se trabaja hoy de forma profesional:
**no se le da una orden suelta, se le da contexto, reglas y un proceso** en archivos del propio repositorio. La IA no recuerda
conversaciones anteriores; lo que está escrito en estos archivos es lo que lee al empezar cada tarea.

| Archivo | Qué contiene |
|---|---|
| [`AGENTS.md`](AGENTS.md) y `CLAUDE.md` | Guía de entrada: qué es el proyecto, stack, comandos, estructura, convenciones y qué comprobar antes de dar algo por terminado |
| [`docs/ia/CONTEXTO.md`](docs/ia/CONTEXTO.md) | El caso resumido: problema, roles, datos, metas y restricciones |
| [`docs/ia/REGLAS.md`](docs/ia/REGLAS.md) | **Instrucciones y restricciones**: sin datos personales, sin secretos, autorización en el servidor, pruebas obligatorias, las acciones de permisos las ejecuta una persona |
| [`docs/ia/ROADMAP.md`](docs/ia/ROADMAP.md) | Fases del proyecto con su estado real y lo pendiente |
| [`docs/ia/DECISIONES.md`](docs/ia/DECISIONES.md) | Decisiones de diseño con su porqué (offline-first, idempotencia, roles, sin datos personales…) |
| [`docs/ia/FLUJO-DE-TRABAJO.md`](docs/ia/FLUJO-DE-TRABAJO.md) | El ciclo de trabajo, quién hace qué y cómo se verifica lo que escribe la IA |
| [`docs/ia/PROMPTS.md`](docs/ia/PROMPTS.md) | Plantillas de instrucciones por tipo de tarea (funcionalidad, error, pruebas, revisión, despliegue) |

**El ciclo de cada tarea:** especificar → la IA propone un plan y una persona lo aprueba → implementar en pasos pequeños →
**verificar ejecutando** (compilación, pruebas, lint, prueba real contra la nube) → una persona revisa el cambio → Pull Request con
integración continua → documentar lo hecho y lo que no se pudo comprobar.

**Qué hace la IA:** propone arquitectura y alternativas, escribe código, pruebas y documentación, diagnostica errores leyendo
registros y código, y ejecuta las verificaciones.
**Qué hacemos nosotros:** definimos y corregimos los requisitos (por ejemplo, que el asistente también es un rol), aprobamos los
planes, revisamos cada cambio, ejecutamos las acciones de permisos en AWS, probamos en el teléfono y en el navegador, y somos
responsables de lo que se entrega y de poder explicarlo.

**Cómo controlamos sus errores:** nada se da por bueno sin ejecutarlo; se rompen reglas a propósito para comprobar que las pruebas
las detectan; se compara el contrato entre la app y el backend; se prueba el flujo real contra el API Gateway desplegado; y antes
de publicar un repositorio se revisa que no haya secretos ni datos reales.

## Arquitectura: controller → service → repository

El backend está en **tres capas**, cada una con una sola responsabilidad. Una petición las atraviesa en orden:

```
 Cliente (app / web)
      │  HTTP + JSON
      ▼
 ┌─────────────┐   valida el cuerpo (@Valid), llama al service y devuelve el estado HTTP
 │ Controller  │   no contiene reglas de negocio
 └─────┬───────┘
       ▼
 ┌─────────────┐   reglas de negocio: "no se elimina un proyecto con eventos",
 │  Service    │   "no hay cupos", "el código lo genera el servidor". Maneja transacciones
 └─────┬───────┘
       ▼
 ┌─────────────┐   acceso a datos: interfaces de Spring Data JPA que generan el SQL
 │ Repository  │
 └─────┬───────┘
       ▼
  PostgreSQL (Supabase)      ← las tablas las crea Flyway, no Hibernate
```

| Capa | Qué hace | Ejemplo real en el código |
|---|---|---|
| **Controller** (`@RestController`) | Recibe la petición, valida el cuerpo y responde. Mapea errores a HTTP | `AsistenteController`: `GET/POST/PUT/DELETE /api/asistentes` |
| **Service** (`@Service`) | La lógica del negocio y las transacciones | `AsistenteService`: genera el código `INS-######` y rechaza borrar a quien tiene asistencias |
| **Repository** (`JpaRepository`) | Consultas a la base | `AsistenteRepository`: `findByUsuarioSub`, búsquedas por código |
| **DTO** | El contrato JSON: lo que entra y sale. Separa la API de las tablas | `AsistenteAltaRequest` (entrada), `AsistenteFilaDto` (salida) |
| **Model / Entity** (`@Entity`) | La tabla como clase | `Asistente`, `Proyecto`, `Evento`, `Asistencia`, `Encuesta`, `Inscripcion` |
| **Excepciones** | Errores de negocio → respuesta clara | `GlobalExceptionHandler`: 404, 409 (`ConflictoException`), 400 de validación |

Por qué así: **cambiar una regla no toca el HTTP ni el SQL**, y cada capa se prueba por separado. Los controllers no hablan con
la base, y los services no saben nada de HTTP. Los paquetes se agrupan por **funcionalidad** (`asistente/`, `evento/`,
`proyecto/`, `inscripcion/`, `sync/`, `informe/`), cada uno con su `controller`, `service` y `dto`.

### Tecnologías y por qué

| Tecnología | Para qué | Por qué |
|---|---|---|
| **Spring Boot 4 / Java 21** | Servidor web y estructura | Estándar de la industria; configuración mínima; inyección de dependencias |
| **Spring Security (resource server JWT)** | Autenticación y autorización | Valida el token de Cognito y traduce `cognito:groups` a `ROLE_*` |
| **Spring Data JPA** | Repositorios | Evita escribir SQL repetitivo |
| **Flyway** | Migraciones de la base (`V1`…`V5`) | El esquema queda versionado y es reproducible |
| **PostgreSQL (Supabase)** | Base de datos | Relacional, gratuita, gestionada |
| **Amazon Cognito** | Usuarios, contraseñas y grupos | No guardamos contraseñas: lo hace un servicio especializado |
| **API Gateway + EC2** | Entrada pública y servidor | El gateway valida el token antes de que llegue a nuestro código |
| **JUnit + MockMvc + H2** | Pruebas | Prueban endpoints reales sin red ni Docker |

## Roles y cómo iniciar sesión

`POST /api/auth/login` entrega los tokens de Cognito. El grupo del usuario (`cognito:groups`) se convierte en rol y cada ruta
declara qué rol exige (`SecurityConfig`). Sin token responde **401**; con un rol que no corresponde, **403**.

| Rol | Cómo se crea la cuenta | Qué puede hacer |
|---|---|---|
| **COORDINADOR** | La crea el equipo de coordinación | Todo: mantenedores, quórum, informes, CSV |
| **MONITOR** | La crea el equipo de coordinación | Catálogo y sincronización de asistencias, altas y encuestas |
| **ASISTENTE** | Se registra solo (`/api/auth/registro-asistente`) | Ver talleres, inscribirse y su QR |

## Mantenedores (CRUD) del backend

| Recurso | Endpoints | Reglas de negocio |
|---|---|---|
| **Proyectos** | `POST/PUT/DELETE /api/proyectos` | Fecha de término ≥ inicio; no se elimina con eventos (409) |
| **Eventos** | `POST/PUT/DELETE /api/eventos` | Cupos ≥ 1, quórum ≤ cupos, GPS completo; no se elimina con asistencias (409) |
| **Asistentes** | `GET/POST/PUT/DELETE /api/asistentes` | Código generado por el servidor; alta de hasta 200; no se elimina con asistencias |
| **Inscripciones** | `POST/DELETE /api/mi/talleres/{id}/inscripcion` | Cupos, no repetir, no inscribirse a un taller pasado |

Los tres mantenedores del coordinador se consumen desde la web y desde la app (pestaña *Gestión*).

## Pruebas y CI

- **Pruebas:** `./gradlew.bat clean build`. Incluyen pruebas de cada capa: servicios, controllers con `MockMvc`, seguridad por
  rol, migraciones y contrato JSON. Ver más abajo el detalle.
- **GitHub Actions** (`.github/workflows/ci.yml`): en cada `push` a `main` y en cada Pull Request compila el proyecto con
  Java 21 y corre todas las pruebas. Si una falla, la PR sale en rojo antes de unirse.

## Roles

Salen de los **grupos de Cognito** (`cognito:groups` → `ROLE_*`):

| Rol | Quién es | Puede |
|---|---|---|
| **COORDINADOR** | Docente / coordinador(a) de proyecto | Todo lo del monitor, más gestionar proyectos y eventos, ver el quórum y descargar el reporte |
| **MONITOR** | Estudiante voluntario en terreno | Descargar el catálogo y subir asistencias, altas de asistentes y encuestas |
| **ASISTENTE** | Beneficiario comunitario | Registrarse él mismo, ver los talleres próximos, inscribirse (con cupos) y mostrar su QR. No accede a nada del equipo (403) |

Los asistentes **no son usuarios**: son un código sintético y un tipo (Estudiante, Vecino o Docente). No existe ningún
campo de nombre, correo, teléfono ni documento.

## Endpoints

Todas las respuestas correctas vienen en `{success, data, message, timestamp}`; los errores, en
`{error, mensaje, status, detalles}`. Rutas protegidas: cabecera `Authorization: Bearer <accessToken>`.

| Método y ruta | Acceso | Para qué |
|---|---|---|
| `GET /api/health` | público | Estado del servicio |
| `POST /api/auth/login` | público | Inicia sesión → `accessToken`, `idToken`, `refreshToken` |
| `POST /api/auth/refresh` | público | Renueva los tokens con el refresh token |
| `POST /api/auth/logout` | con token | Revoca el refresh token |
| `POST /api/auth/olvide-contrasena/solicitar` y `/confirmar` | público | Recuperación de contraseña |
| `POST /api/auth/registro-asistente` | público | El asistente crea su cuenta (correo, contraseña y tipo). Queda en el grupo ASISTENTE y con un código sintético `REG-XXXXXXXX`; la base **no** guarda su correo ni su nombre |
| `GET /api/mi/perfil` · `GET /api/mi/talleres` | ASISTENTE | Mi código y QR; talleres próximos con cupos disponibles y si ya estoy inscrito |
| `POST/DELETE /api/mi/talleres/{id}/inscripcion` | ASISTENTE | Inscribirme o cancelar (409 si ya estaba inscrito, no hay cupos o el taller ya ocurrió) |
| `POST /api/auth/registro` | público* | Auto-registro (rol MONITOR). *No se publica en API Gateway sin token |
| `GET /api/sync/catalogo?desde=` | autenticado | Proyectos, eventos y asistentes; `desde` = `servidorEn` anterior (incremental) |
| `POST /api/sync/asistentes` | autenticado | Alta de asistentes llegados **sin inscripción previa** (por lotes, idempotente) |
| `POST /api/sync/asistencias` | autenticado | Marcaciones de asistencia (por lotes, idempotente) |
| `POST /api/sync/encuestas` | autenticado | Encuestas de satisfacción y utilidad (por lotes, idempotente) |
| `GET /api/eventos/{id}/resumen` | COORDINADOR | Quórum: marcados, mínimo y si está cumplido, dentro/fuera del radio, encuestas y promedios |
| `GET /api/eventos/{id}/asistencias` | COORDINADOR | Una fila por asistencia con su encuesta (base del CSV) |
| `GET /api/eventos/{id}/comentarios` | COORDINADOR | Comentarios de las encuestas del evento, sin nada que identifique a quien los escribió |
| `GET /api/informes[?proyectoId=]` | COORDINADOR | Informe de impacto consolidado (por proyecto o general): quórum, tasa de respuesta, promedios ponderados, comentarios |
| `GET/POST /api/asistentes` · `PUT/DELETE /api/asistentes/{id}` | COORDINADOR | Lista de asistentes y alta (uno o hasta 200) con código generado por el servidor; edición de tipo e inscripción; baja solo si no tiene asistencias |
| `POST/PUT/DELETE /api/proyectos[/{id}]` | COORDINADOR | Gestión de proyectos |
| `POST/PUT/DELETE /api/eventos[/{id}]` | COORDINADOR | Gestión de eventos |
| `POST /api/notificar` | autenticado | Relé de notificaciones (ntfy) |

### Reglas de la sincronización por lotes

- **Un lote es un objeto con una lista** (`{"registros": [...]}`, máximo 500), y responde **200 aunque haya registros
  rechazados**: el resultado de cada uno va en el cuerpo (`ACEPTADO`, `DUPLICADO` o `RECHAZADO` con su `motivo`).
- **Cada registro es independiente** y corre en su propia transacción: uno inválido no tumba a los demás.
- **Idempotencia**: el `uuid` que genera el teléfono es la clave. Reenviar un lote es inocuo; `ACEPTADO` y `DUPLICADO`
  significan lo mismo: el servidor ya lo tiene.
- **Quién registró** sale del `sub` del JWT, nunca del cuerpo.
- **El GPS solo clasifica** (`VALIDA`, `FUERA_DE_RADIO`, `SIN_UBICACION`) y **nunca rechaza** una marcación.
- **Alta en terreno**: el servidor fija `SIN_INSCRIPCION` y genera el código `TER-` + 12 hexadecimales del uuid; el
  cliente no decide ninguno de los dos. Debe subirse antes que las asistencias que lo usan.
- **Borrado**: no se elimina un proyecto con eventos ni un evento con asistencias (409): no se pierde evidencia de terreno.

## Base de datos

PostgreSQL en Supabase (Session pooler, puerto 5432; el 6543 no sirve para Flyway). **Flyway** es la única autoridad
sobre el esquema (`ddl-auto: none`):

| Migración | Contenido |
|---|---|
| `V1__baseline_usuarios.sql` | `usuarios` y `usuario_roles` (lo que ya existía) |
| `V2__esquema_syncpass.sql` | `proyectos`, `eventos`, `asistentes`, `asistencias`, `encuestas` |
| `V3__asistentes_uuid.sql` | `asistentes.uuid` (opcional, único): clave de idempotencia del alta en terreno |
| `V4__eventos_quorum_minimo.sql` | `eventos.quorum_minimo` (opcional): mínimo de asistentes para dar por cumplido el quórum |
| `db/demo/R__datos_demo.sql` | Catálogo **ficticio**; solo con el perfil `demo` |

Una base que ya tiene tablas y no tiene historial se **baselinea** en V1 y ejecuta solo lo posterior. Detalle y pasos
de verificación en [`docs/MIGRACIONES.md`](docs/MIGRACIONES.md).

## Cómo ejecutarla

Requisitos: **JDK 21** y un PostgreSQL (el de Supabase o uno local, p. ej. `docker run -p 5432:5432 -e POSTGRES_PASSWORD=... postgres:16`).

```
cp .env.example .env          # en PowerShell: Copy-Item .env.example .env; completa los valores
./gradlew.bat bootRun         # o: ./gradlew.bat bootJar && java -jar build/libs/cursos-0.0.1-SNAPSHOT.jar
```

Perfiles de Spring (`SPRING_PROFILES_ACTIVE`, variable de entorno real, no del `.env`):

| Perfil | Efecto |
|---|---|
| *(ninguno)* | Desarrollo: valores por defecto, SQL visible en el log |
| `prod` | Exige las variables de base de datos (no cae a localhost) y fuerza SSL |
| `demo` | Carga el catálogo ficticio. **Nunca solo en producción real**: la API no tiene endpoint para sembrar datos |

Variables (solo los nombres; los valores van en tu `.env`, que **no se sube a Git**): `DATABASE_URL`, `DATABASE_USER`,
`DATABASE_PASSWORD`, `AWS_REGION`, `AWS_COGNITO_USER_POOL_ID`, `AWS_COGNITO_CLIENT_ID`, `AWS_COGNITO_CLIENT_SECRET`
(vacía si el app client no tiene secreto), `CORS_ENABLED`, `CORS_ALLOWED_ORIGINS`. Guía completa en
[`docs/ENVS.md`](docs/ENVS.md).

## Detalle de las pruebas

```
./gradlew.bat clean build      # 201 pruebas, 0 fallos
```

Sin Docker ni red: H2 en modo PostgreSQL y un `JwtDecoder` simulado. Cubren el contrato JSON de cada endpoint, la
idempotencia, los lotes parciales, la seguridad por rol (401 sin token, 403 con otro rol), las reglas de borrado, la
privacidad del reporte y las migraciones (base vacía, base existente y V3 sobre una base en V2). Se comprobó que quitar
a propósito una regla de seguridad hace fallar la prueba de permisos.

**No comprobado en pruebas automáticas**: el comportamiento exacto de PostgreSQL con cada migración (H2 es solo
compatible) —se verificó al desplegar, con el log de Flyway— y el flujo completo con Cognito real.

## Despliegue

JAR como servicio `systemd` en una EC2 con Amazon Linux 2023, detrás de un API Gateway HTTP con authorizer JWT de
Cognito. Todo el procedimiento, los scripts y las decisiones están en [`deploy/README.md`](deploy/README.md):

```
EC2_HOST=<ip-elastica> SSH_KEY=./syncpass-key.pem bash deploy/subir-jar.sh
```

Los secretos viven solo en `/etc/syncpass/syncpass.env` (root, `chmod 600`) dentro de la EC2.

## Seguridad

- **Nunca** se versionan `.env`, `*.pem` ni claves (`.gitignore`). Si un secreto se filtra: rotarlo, no solo borrarlo.
- El token se valida contra el *issuer* del User Pool; los roles salen de `cognito:groups`.
- Pendiente conocido: no se valida el `aud`/`token_use`, así que un *id token* también se acepta como credencial.
- `/api/sync/**` y `/api/notificar` son solo de MONITOR y COORDINADOR: una cuenta de asistente recibe 403.
- `/api/auth/registro-asistente` es pública a propósito y solo puede crear el rol ASISTENTE (lo fija el servidor).
- `/api/auth/registro` crearía cuentas MONITOR sin control; por eso no se expone en API Gateway sin token y las cuentas
  las crea el equipo de coordinación.

## Límites conocidos

- **Los borrados no se propagan a teléfonos ya sincronizados**: el catálogo incremental solo envía lo nuevo o
  modificado. Por eso solo se borra lo que no tiene asistencias.
- No hay modelo de propiedad usuario↔proyecto: todo coordinador puede editar cualquier proyecto.
- **La EC2 necesita el rol IAM `LabInstanceProfile`** (EC2 → Acciones → Seguridad → Modificar rol de IAM). Sin él, el servidor arranca
  y el login funciona, pero crear cuentas falla con 503 (`Unable to load credentials`): el registro necesita confirmar al usuario en Cognito.
- En un AWS Academy Learner Lab la EC2 se apaga al terminar la sesión; el servicio arranca solo al iniciarla, pero la
  instancia hay que iniciarla a mano.

## Documentación adicional

[`docs/ENVS.md`](docs/ENVS.md) · [`docs/MIGRACIONES.md`](docs/MIGRACIONES.md) ·
[`deploy/README.md`](deploy/README.md).

## Equipo

Proyecto del curso DSY1105 · Desarrollo de Aplicaciones Móviles · Duoc UC.

| Integrante | GitHub | Aporte en este repositorio |
|---|---|---|
| Mario Meléndez | [Mario-Melendez-Inostroza](https://github.com/Mario-Melendez-Inostroza) | Desarrollo e integración del sistema |
| _(nombre completo)_ | [bx1423](https://github.com/bx1423) | Pruebas del DTO del perfil del asistente (`MiPerfilDtoTest`) |
| _(nombre completo)_ | [Monnacohu](https://github.com/Monnacohu) | Pruebas de los enums `TipoParticipante` y `EstadoInscripcion` (`TipoParticipanteTest`) |
