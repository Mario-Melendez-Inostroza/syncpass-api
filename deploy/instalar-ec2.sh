#!/usr/bin/env bash
# Se ejecuta EN LA EC2 (Amazon Linux 2023). Lo invoca subir-jar.sh; también se puede correr a mano:
#   sudo bash ~/syncpass-deploy/instalar-ec2.sh
# Es idempotente: se puede repetir en cada despliegue. No imprime ni toca los secretos.
set -euo pipefail

ORIGEN="$(cd "$(dirname "$0")" && pwd)"
APP_DIR=/opt/syncpass
ENV_DIR=/etc/syncpass
ENV_FILE="$ENV_DIR/syncpass.env"

[ "$(id -u)" -eq 0 ] || { echo "Ejecuta con sudo."; exit 1; }
for f in syncpass.jar syncpass.service ec2.env.example; do
  [ -f "$ORIGEN/$f" ] || { echo "Falta $ORIGEN/$f"; exit 1; }
done

# 1) Java 21 (headless basta para un servicio)
if ! java -version 2>&1 | grep -q 'version "21'; then
  dnf install -y java-21-amazon-corretto-headless
fi

# 2) Usuario de servicio sin shell y carpetas
id syncpass >/dev/null 2>&1 || useradd --system --home-dir "$APP_DIR" --shell /sbin/nologin syncpass
install -d -o syncpass -g syncpass -m 750 "$APP_DIR"
install -d -o root -g root -m 700 "$ENV_DIR"

# 3) JAR (conserva el anterior para volver atrás)
[ -f "$APP_DIR/syncpass.jar" ] && cp -p "$APP_DIR/syncpass.jar" "$APP_DIR/syncpass.jar.anterior"
install -o syncpass -g syncpass -m 640 "$ORIGEN/syncpass.jar" "$APP_DIR/syncpass.jar"

# 4) Archivo de variables: solo se crea si no existe; nunca se sobrescribe
if [ ! -f "$ENV_FILE" ]; then
  install -o root -g root -m 600 "$ORIGEN/ec2.env.example" "$ENV_FILE"
  echo "Creado $ENV_FILE desde la plantilla."
fi
chown root:root "$ENV_FILE"; chmod 600 "$ENV_FILE"   # systemd lo lee como root

# 5) Unit de systemd
install -o root -g root -m 644 "$ORIGEN/syncpass.service" /etc/systemd/system/syncpass.service
systemctl daemon-reload
systemctl enable syncpass >/dev/null

# 6) No arrancar con la plantilla sin completar
if grep -qE "<[a-z0-9áéíóúñ-]+>" "$ENV_FILE"; then
  echo
  echo "FALTA completar $ENV_FILE (quedan <marcadores>). Edítalo y luego:"
  echo "  sudo nano $ENV_FILE"
  echo "  sudo systemctl restart syncpass"
  exit 0
fi

systemctl restart syncpass
echo "Servicio reiniciado. Esperando el arranque (Flyway puede tardar)..."
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "OK: /api/health responde."
    exit 0
  fi
  sleep 3
done
echo "No respondió en 2 minutos. Revisa: sudo journalctl -u syncpass -n 80 --no-pager"
exit 1
