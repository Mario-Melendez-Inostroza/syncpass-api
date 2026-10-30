# SyncPass · API (backend) — guía para la IA

API REST que autentica contra Amazon Cognito, guarda en PostgreSQL (Supabase) y es el único punto de entrada de la app Android y del panel web.

**Stack:** Java 21 · Spring Boot 4 · Spring Security (resource server JWT) · Spring Data JPA · Flyway · PostgreSQL · JUnit, MockMvc y H2

Antes de tocar nada lee `docs/ia/REGLAS.md` y `docs/ia/CONTEXTO.md`; las decisiones ya tomadas están en `docs/ia/DECISIONES.md` y
el estado del proyecto en `docs/ia/ROADMAP.md`.

## Comandos

```
./gradlew.bat clean build        # compila y corre todas las pruebas (debe quedar en verde)
./gradlew.bat test               # solo pruebas
EC2_HOST=<ip> SSH_KEY=<clave> bash deploy/subir-jar.sh    # despliegue (lo ejecuta una persona)
```

## Estructura

```
src/main/java/com/duoc/cursos/
  <funcionalidad>/controller|service|dto   asistente, evento, proyecto, inscripcion, sync, informe
  model/ repository/ config/ exception/    entidades, acceso a datos, seguridad, errores
src/main/resources/db/migration/           V1 … V5 (Flyway)
deploy/                                    systemd, API Gateway, scripts
```

## Convenciones

- Capas **controller → service → repository**: el controller valida (`@Valid`) y responde; el service tiene las reglas y las
  transacciones; el repository solo accede a datos. Ningún controller toca la base.
- El contrato JSON usa DTO separados de las entidades. Las respuestas van en `ApiResponse{success, data, message, timestamp}` y los
  errores en `{error, mensaje, status, detalles}` (`GlobalExceptionHandler`).
- **Nunca se edita una migración ya aplicada:** se crea la siguiente (`V6__…`). Hibernate no modifica el esquema (`ddl-auto: none`).
- Cada ruta declara el rol que exige en `SecurityConfig`; una ruta nueva sin regla no debe quedar abierta.
- Los registros de sincronización son idempotentes por `uuid`.

## Antes de dar algo por terminado

1. `./gradlew.bat clean build` sin fallos.
2. Hay pruebas para lo nuevo y fallan si se rompe la regla a propósito.
3. No se versionó ningún secreto, dato personal ni archivo de IDE.
4. El README y `docs/ia/ROADMAP.md` reflejan lo hecho, y lo no comprobado está declarado.
