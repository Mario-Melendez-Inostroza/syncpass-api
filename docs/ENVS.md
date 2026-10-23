# Variables de entorno de fs2-api

Todos los valores de este documento son **ficticios**: los reales los escribes tú en tu `.env` local, que **nunca se sube a Git** ni se comparte.

## Cómo se cargan las variables

1. `CursosApplication.main` llama a `loadDotenv()` **antes** de arrancar Spring. Busca un archivo `.env` en el directorio desde el que se ejecuta la aplicación (la carpeta `fs2-api`) con la librería `java-dotenv`.
2. Cada variable del `.env` se copia a una propiedad del sistema de Java **solo si** no existe ya como propiedad (`-D...`) ni como variable de entorno real.
3. Spring resuelve los marcadores `${NOMBRE}` de `application.yml` y `application-prod.yml` contra esas propiedades.

Prioridad: **variable de entorno real > `.env` > valor por defecto de `application.yml`**.

Consecuencia importante: el `.env` solo alimenta a los marcadores `${NOMBRE}` que existen en los YAML. Lo siguiente **no** se puede fijar desde el `.env`:

| Qué | Cómo se pasa |
|---|---|
| Perfil de Spring (`prod`) | Argumento `--spring.profiles.active=prod`, o la variable de entorno **real** `SPRING_PROFILES_ACTIVE=prod` |
| Puerto del servidor | Argumento `--server.port=8081`, o la variable de entorno real `SERVER_PORT` |
| Credenciales de AWS (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`) | Variables de entorno reales de tu terminal; en EC2, el rol de la instancia (ver más abajo) |

(`LOG_LEVEL` figuraba en documentos anteriores, pero ningún archivo la usa: no tiene efecto.)

## Crear y completar tu `.env` (desarrollo local)

Desde PowerShell, en la carpeta del proyecto:

```powershell
cd C:\Users\black\Desktop\Proyectos\SyncPass-Analisis\fs2-api
Copy-Item .env.example .env
code .env        # o tu editor preferido
```

Reemplaza cada `<marcador>` por tu valor real (los de Supabase y Cognito están en la tabla siguiente) y guarda.

Reglas del formato (las aplica `java-dotenv`; verificado con la versión 5.2.2):

- Una línea `CLAVE=VALOR` por variable, o comentarios que empiezan con `#`. **Una línea sin `=` hace fallar el arranque**, y el mensaje de error repite esa línea.
- **No pongas comentarios al final de una línea de valor**: `X=valor # nota` incluye `# nota` en el valor.
- Las comillas dobles se quitan; las simples se conservan. Lo más seguro es no usar comillas.
- `#`, `$` y `=` dentro de un valor se respetan tal cual. Los espacios al principio y al final se recortan.
- Los finales de línea de Windows (CRLF) funcionan.
- **Guárdalo como UTF-8 sin BOM.** Con BOM, la primera línea falla con `Malformed entry` y el mensaje repite esa línea. VS Code y el Bloc de notas de Windows 11 guardan sin BOM; `Set-Content` y `Out-File -Encoding UTF8` de Windows PowerShell 5.1 **sí** lo añaden.

Comprobar que Git lo ignora (no debe aparecer en `git status`):

```powershell
git check-ignore -v .env      # debe mostrar la regla de .gitignore que lo ignora
git status --short            # .env no debe aparecer
```

## Iniciar la aplicación con esa configuración

Desde la carpeta `fs2-api` (para que encuentre el `.env`):

```powershell
.\gradlew.bat bootJar
java -jar build\libs\cursos-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

- Para trabajar contra Supabase se recomienda el perfil `prod` (ver abajo).
- Si lanzas el JAR desde otra carpeta, no encontrará el `.env` y usará los valores por defecto.
- Al arrancar, el mensaje `Dotenv cargado: N variables` cuenta **todas** las variables del sistema además de las del `.env`; no indica cuántas hay en tu archivo.

## Tres escenarios

| Escenario | Perfil | Dónde van las variables | Notas |
|---|---|---|---|
| Desarrollo local | `default` (sin argumento) | `.env` | `show-sql` activado; SSL solo si la URL trae `sslmode`; hay valores por defecto (localhost, `postgres/postgres`) |
| Local contra Supabase | `prod` (`--spring.profiles.active=prod`) | `.env` | Sin valores por defecto para la base: si falta una variable, no arranca. Fuerza SSL y desactiva `show-sql` |
| Servidor (EC2, futuro) | `prod` (`SPRING_PROFILES_ACTIVE=prod` real) | Variables de entorno del servidor, p. ej. `EnvironmentFile` de systemd con permisos `600` | Credenciales de AWS por el **rol de la instancia**, no por variables ni archivos |

## Variables

### Base de datos con Supabase (Session pooler)

| Variable | Valor | Dónde obtenerlo |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<host-del-session-pooler>:5432/<base>?sslmode=require` | Panel de Supabase → botón **Connect** → *Session pooler* (host, puerto y base) |
| `DATABASE_USER` | `postgres.<ref-del-proyecto>` | El mismo panel (el usuario del pooler incluye la referencia del proyecto) |
| `DATABASE_PASSWORD` | `<contraseña-de-la-base>` | La que definiste al crear el proyecto (Project Settings → Database) |
| `DATABASE_POOL_MAX_SIZE` | `5` (opcional) | Máximo de conexiones del pool; por defecto 5 |

- **`DATABASE_URL` debe ser una URL JDBC** (`jdbc:postgresql://...`). La cadena `postgresql://usuario:clave@host/...` que muestra Supabase **no sirve**. Tampoco se incluyen usuario y contraseña dentro de la URL: van en `DATABASE_USER` y `DATABASE_PASSWORD`.
- **SSL**: usa `?sslmode=require` en la URL. Con el perfil `prod` la aplicación además exige SSL aunque la URL no lo indique; si la URL trae su propio `sslmode`, ese valor prevalece.
- **Perfil `prod`**: no hay valores por defecto para `DATABASE_URL`, `DATABASE_USER` ni `DATABASE_PASSWORD`. Si falta alguno, la aplicación **no arranca** (código de salida 1; el error nombra la variable, p. ej. `claims to not accept jdbcUrl, ${DATABASE_URL}`) en lugar de conectarse a localhost o usar `postgres/postgres`.
- **Pool**: el plan gratuito de Supabase tiene pocas conexiones y, en el Session pooler, cada conexión del pool ocupa una real. Por eso el pool por defecto es de 5.
- **Session pooler, no el de transacción** (puerto 6543): Hibernate usa sentencias preparadas, que el modo transacción no soporta bien.
- **Tablas**: con `ddl-auto: update`, el primer arranque crea `usuarios` y `usuario_roles` en el esquema `public`. Revisa en Supabase que esas tablas no queden expuestas por su API REST.

### AWS Cognito

| Variable | Valor | Dónde obtenerlo |
|---|---|---|
| `AWS_REGION` | `us-east-1` | Región de tu User Pool |
| `AWS_COGNITO_USER_POOL_ID` | `<id-del-user-pool>` | Consola de Cognito → tu pool → *User pool overview* |
| `AWS_COGNITO_CLIENT_ID` | `<id-del-app-client>` | Tu pool → *App clients* |
| `AWS_COGNITO_CLIENT_SECRET` | `<secreto>` o vacío | Solo si el app client tiene secreto |

Las variables de Cognito tienen valores de relleno por defecto (`us-east-1_xxxxxxxxx`): si olvidas una, la aplicación arranca igual y falla recién al registrar.

### CORS (opcionales)

`CORS_ENABLED`, `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, `CORS_ALLOWED_HEADERS`, `CORS_EXPOSED_HEADERS`, `CORS_ALLOW_CREDENTIALS` y `CORS_MAX_AGE`. Los valores por defecto están en `.env.example`; para Postman no hacen falta.

## Credenciales de AWS (no van en `.env`)

La API necesita credenciales AWS para `AdminConfirmSignUp` y `AdminAddUserToGroup`. **No las pongas en el `.env`**: el cargador las deja como propiedades del sistema con el nombre `AWS_ACCESS_KEY_ID`..., y el SDK de AWS solo busca `aws.accessKeyId`... o las variables de entorno reales (verificado).

- En EC2: usa el rol (perfil de instancia) asociado a la instancia; no hay que definir variables.
- En local: define `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` como variables de entorno de tu sesión de terminal, antes de lanzar el JAR (en Learner Lab son temporales y caducan).

## Test user

`TEST_USER_EMAIL=test@example.com` y `TEST_USER_PASSWORD=<contraseña-de-prueba>` son solo una guía manual, no las lee la aplicación:

- Para validar que la integración con Cognito es correcta.
- Validar con curl levantando en localhost.

## Seguridad

- El `.env` real está en `.gitignore` (junto con `.env.*`, `*.pem`, `*.key`, `*.p12`, `*.jks`, `application-local.*` y `.aws/`). `.env.example` sí se versiona y solo contiene marcadores.
- No pegues contraseñas ni credenciales en chats, issues ni capturas de pantalla.
- Si una credencial se expone, cámbiala (Supabase: *Reset database password*; AWS: genera credenciales nuevas).
