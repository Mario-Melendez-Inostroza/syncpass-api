# Variables de Entorno - Guía de Configuración

Este documento describe todas las variables de entorno necesarias para ejecutar la aplicación en diferentes entornos.

---

## Variables Requeridas

### AWS Cognito
| Variable | Descripción | Ejemplo | Requerida |
|----------|-------------|---------|-----------|
| `AWS_REGION` | Región de AWS donde está el User Pool | `us-east-1` | **Sí** |
| `AWS_COGNITO_USER_POOL_ID` | ID del User Pool de Cognito | `us-east-1_AbCdEfGhI` | **Sí** |
| `AWS_COGNITO_CLIENT_ID` | ID del App Client de Cognito | `1a2b3c4d5e6f7g8h9i0j` | **Sí** |
| `AWS_COGNITO_CLIENT_SECRET` | Secreto del App Client (si está habilitado) | `abcdefghijklmnopqrstuvwxyz` | No* |

> *Requerida solo si el App Client tiene **"Generate client secret"** activado en la consola de Cognito.

### Base de Datos (PostgreSQL)
| Variable | Descripción | Ejemplo | Requerida |
|----------|-------------|---------|-----------|
| `DATABASE_URL` | URL de conexión JDBC completa | `jdbc:postgresql://localhost:5432/cursos_db` | **Sí** |
| `DATABASE_USER` | Usuario de la base de datos | `cursos_user` | **Sí** |
| `DATABASE_PASSWORD` | Contraseña del usuario | `secure_password_123` | **Sí** |

---

## Variables Opcionales (con defaults)

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `default` | Perfil de Spring activo. Solo existe configuración para `prod` (`application-prod.yml`): exige `DATABASE_*`, fuerza SSL y desactiva `show-sql`. **Solo funciona como variable de entorno real o como argumento `--spring.profiles.active=prod`, no desde el `.env`.** Ver `docs/ENVS.md` |
| `DATABASE_POOL_MAX_SIZE` | `5` | Máximo de conexiones del pool de base de datos (el plan gratuito de Supabase tiene pocas) |
| `SERVER_PORT` | `8080` | Puerto del servidor HTTP. Solo como variable de entorno real o `--server.port=...`; no desde el `.env` |
| `LOG_LEVEL` | `INFO` | **Sin efecto actualmente**: ningún archivo de configuración la utiliza |
| `CORS_ENABLED` | `true` | Habilita/deshabilita CORS completamente. Poner `false` cuando la app pasa por API Gateway que maneja CORS (evita doble CORS) |
| `CORS_ALLOWED_ORIGINS` | `*` | Orígenes permitidos separados por coma. Usa `*` para todos. Ej: `https://app.midominio.com,https://admin.midominio.com` |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Métodos HTTP permitidos |
| `CORS_ALLOWED_HEADERS` | `*` | Headers permitidos en requests |
| `CORS_EXPOSED_HEADERS` | `Authorization,Content-Type` | Headers expuestos al cliente |
| `CORS_ALLOW_CREDENTIALS` | `false` | Permitir credenciales (cookies, auth headers). Debe ser `false` si `CORS_ALLOWED_ORIGINS=*` |
| `CORS_MAX_AGE` | `3600` | Tiempo de cache para preflight requests (segundos) |

---

## Configuración por Entorno

### Desarrollo Local (`.env.local` o variables de shell)
```bash
# AWS Cognito (usar valores de tu User Pool de desarrollo)
export AWS_REGION=us-east-1
export AWS_COGNITO_USER_POOL_ID=us-east-1_xxxxxxxxx
export AWS_COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
export AWS_COGNITO_CLIENT_SECRET=  # Vacío si no hay client secret

# Base de datos local (Docker)
export DATABASE_URL=jdbc:postgresql://localhost:5432/cursos_dev
export DATABASE_USER=postgres
export DATABASE_PASSWORD=postgres

# App
export SERVER_PORT=8080
export LOG_LEVEL=DEBUG
```

### Docker Compose (desarrollo)
```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: cursos_dev
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  app:
    build: .
    environment:
      AWS_REGION: ${AWS_REGION}
      AWS_COGNITO_USER_POOL_ID: ${AWS_COGNITO_USER_POOL_ID}
      AWS_COGNITO_CLIENT_ID: ${AWS_COGNITO_CLIENT_ID}
      AWS_COGNITO_CLIENT_SECRET: ${AWS_COGNITO_CLIENT_SECRET}
      DATABASE_URL: jdbc:postgresql://postgres:5432/cursos_dev
      DATABASE_USER: postgres
      DATABASE_PASSWORD: postgres
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

### Producción
```bash
# Usar secrets manager / parameter store / vault
export AWS_REGION=us-east-1
export AWS_COGNITO_USER_POOL_ID=us-east-1_ProdAbCdEf
export AWS_COGNITO_CLIENT_ID=prod_client_id_xxxxxxxxxx
export AWS_COGNITO_CLIENT_SECRET=prod_secret_xxxxxxxxxxxx

export DATABASE_URL=jdbc:postgresql://prod-db.cluster-xyz.us-east-1.rds.amazonaws.com:5432/cursos_prod
export DATABASE_USER=cursos_prod_user
export DATABASE_PASSWORD=${DB_PASSWORD_FROM_VAULT}

export SERVER_PORT=8080
export LOG_LEVEL=WARN
export SPRING_PROFILES_ACTIVE=prod
```

### CI/CD (GitHub Actions / GitLab CI)
```yaml
# .github/workflows/ci.yml
env:
  AWS_REGION: us-east-1
  AWS_COGNITO_USER_POOL_ID: ${{ secrets.COGNITO_USER_POOL_ID }}
  AWS_COGNITO_CLIENT_ID: ${{ secrets.COGNITO_CLIENT_ID }}
  AWS_COGNITO_CLIENT_SECRET: ${{ secrets.COGNITO_CLIENT_SECRET }}
  DATABASE_URL: jdbc:postgresql://localhost:5432/cursos_test
  DATABASE_USER: postgres
  DATABASE_PASSWORD: postgres
```

---

## Verificación de Configuración

### Health Check
```bash
curl http://localhost:8080/api/health
# Respuesta esperada:
# {"status":"UP","timestamp":"2026-10-22T...","service":"cursos-api"}
```

### Test de Conectividad Cognito (manual)
```bash
# Verificar que el User Pool existe y credenciales son válidas
aws cognito-idp describe-user-pool --user-pool-id $AWS_COGNITO_USER_POOL_ID --region $AWS_REGION
```

---

## Checklist de Despliegue

- [ ] User Pool creado en Cognito
- [ ] App Client creado **sin** "Generate client secret" (para flujo USER_PASSWORD_AUTH) **O** con secret y variable `AWS_COGNITO_CLIENT_SECRET` configurada
- [ ] App Client tiene habilitado `ALLOW_USER_PASSWORD_AUTH` en Auth Flows
- [ ] Grupos (roles) creados en Cognito: `ADMIN`, `USER`, `INSTRUCTOR`, etc.
- [ ] Base de datos PostgreSQL accesible desde la app
- [ ] Migración inicial ejecutada (Hibernate `ddl-auto: update` o Flyway/Liquibase)
- [ ] Variables de entorno inyectadas en el contenedor/servidor
- [ ] Health check responde `200 OK`