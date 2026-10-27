#!/usr/bin/env bash
# Se ejecuta EN TU PC (Git Bash) desde fs2-api/. Crea /etc/syncpass/syncpass.env en la EC2 a partir de tu .env local,
# sin imprimir ningún valor: viajan por SSH (stdin) directo al archivo, que queda root:root con chmod 600.
#
#   EC2_HOST=<ip-elastica> SSH_KEY=./syncpass-key.pem bash deploy/crear-env-remoto.sh
#
# Copia solo lo que el servicio necesita. No copia AWS_COGNITO_CLIENT_SECRET: el app client actual no tiene secreto.
# No sobrescribe un archivo existente salvo que pases FORZAR=1.
set -euo pipefail

: "${EC2_HOST:?Define EC2_HOST}"
: "${SSH_KEY:?Define SSH_KEY}"
EC2_USER="${EC2_USER:-ec2-user}"
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "No hay .env local"; exit 1; }

valor() {  # lee CLAVE de .env: quita CR y comillas dobles envolventes
  local v
  v=$(grep -E "^$1=" .env | head -1 | cut -d= -f2- | tr -d '\r')
  v="${v%\"}"; v="${v#\"}"
  [ -n "$v" ] || { echo "Falta $1 en .env" >&2; exit 1; }
  printf '%s' "$v"
}

SSH=(ssh -i "$SSH_KEY" -o BatchMode=yes "$EC2_USER@$EC2_HOST")
if [ "${FORZAR:-0}" != "1" ] && "${SSH[@]}" 'sudo test -f /etc/syncpass/syncpass.env'; then
  echo "Ya existe /etc/syncpass/syncpass.env; no se toca (usa FORZAR=1 para reemplazarlo)."
  exit 0
fi

{
  echo "SPRING_PROFILES_ACTIVE=prod,demo"
  echo "DATABASE_URL=$(valor DATABASE_URL)"
  echo "DATABASE_USER=$(valor DATABASE_USER)"
  echo "DATABASE_PASSWORD=$(valor DATABASE_PASSWORD)"
  echo "DATABASE_POOL_MAX_SIZE=5"
  echo "AWS_REGION=$(valor AWS_REGION)"
  echo "AWS_COGNITO_USER_POOL_ID=$(valor AWS_COGNITO_USER_POOL_ID)"
  echo "AWS_COGNITO_CLIENT_ID=$(valor AWS_COGNITO_CLIENT_ID)"
  echo "AWS_COGNITO_CLIENT_SECRET="
  echo "CORS_ENABLED=false"
} | "${SSH[@]}" 'sudo install -d -o root -g root -m 700 /etc/syncpass && sudo tee /etc/syncpass/syncpass.env >/dev/null && sudo chown root:root /etc/syncpass/syncpass.env && sudo chmod 600 /etc/syncpass/syncpass.env'

echo "Creado /etc/syncpass/syncpass.env (root:root, 600). Variables: 9 (valores no mostrados)."
