# Despliegue del backend en EC2

JAR de Spring Boot (Java 21) como servicio `systemd` en Amazon Linux 2023, detrás de API Gateway.
`Usuario → API Gateway (HTTP API, authorizer JWT de Cognito) → EC2:8080 → Cognito / Supabase`

Esta carpeta no contiene secretos. Los reales viven solo en `/etc/syncpass/syncpass.env` dentro de la EC2.

## Antes de la primera vez
1. **Paso 0 de `docs/MIGRACIONES.md`**: inspeccionar el esquema real de Supabase. El primer arranque aplica `V2` y,
   con el perfil `demo`, siembra el catálogo ficticio.
2. La EC2 necesita: Elastic IP, Security Group con 22 (solo tu IP) y 8080 abiertos, y un **rol IAM** adjunto
   (la API llama `AdminConfirmSignUp`, `AdminAddUserToGroup` y `AdminDeleteUser` al registrar usuarios;
   el SDK toma las credenciales del rol, nunca de un archivo).

## Desplegar (desde fs2-api, en Git Bash)
0. Primera vez: `EC2_HOST=<ip> SSH_KEY=./syncpass-key.pem bash deploy/crear-env-remoto.sh` crea `/etc/syncpass/syncpass.env` en la EC2
   desde tu `.env` local, sin imprimir valores (y sin el secreto del client viejo). Luego:

```
EC2_HOST=<ip-elastica> SSH_KEY=/c/ruta/syncpass-key.pem bash deploy/subir-jar.sh
```
Compila el JAR, lo sube e instala. La primera vez deja `/etc/syncpass/syncpass.env` con la plantilla y no arranca:
edítalo en la EC2 (`sudo nano /etc/syncpass/syncpass.env`) y reinicia (`sudo systemctl restart syncpass`).
Si SSH rechaza el `.pem` por permisos en Windows: `icacls <ruta.pem> /inheritance:r /grant:r "%USERNAME%:R"`.

## Operar
| Qué | Comando (en la EC2) |
|---|---|
| Estado | `sudo systemctl status syncpass` |
| Logs | `sudo journalctl -u syncpass -n 100 --no-pager` (`-f` para seguir) |
| Salud | `curl http://localhost:8080/api/health` |
| Volver atrás | `sudo cp /opt/syncpass/syncpass.jar.anterior /opt/syncpass/syncpass.jar && sudo systemctl restart syncpass` |

## Decisiones a tener presentes
- `SPRING_PROFILES_ACTIVE=prod,demo`: la API no tiene endpoints para crear catálogo, así que sin `demo` no hay
  eventos que descargar. Los datos son ficticios. Quita `,demo` si algún día hay datos reales.
- `CORS_ENABLED=true`: el preflight `OPTIONS` pasa por API Gateway (ruta sin authorizer) y lo contesta el backend. Con CORS apagado da 403 y la web no puede llamar a la API.
- Con 8080 abierto, la EC2 es alcanzable sin pasar por API Gateway; el backend igual valida el JWT de Cognito
  en cada ruta protegida, así que no queda desprotegido.
