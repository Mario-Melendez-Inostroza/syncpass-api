#!/usr/bin/env bash
# Se ejecuta EN TU PC (Git Bash) desde fs2-api/. Sube el JAR y los archivos de deploy a la EC2 y los instala.
#
#   EC2_HOST=<ip-elastica> SSH_KEY=/c/ruta/syncpass-key.pem bash deploy/subir-jar.sh
#
# Opcionales: EC2_USER (por defecto ec2-user), BUILD=0 para no recompilar.
set -euo pipefail

: "${EC2_HOST:?Define EC2_HOST (IP elástica o DNS de la EC2)}"
: "${SSH_KEY:?Define SSH_KEY (ruta al .pem)}"
EC2_USER="${EC2_USER:-ec2-user}"
cd "$(dirname "$0")/.."

if [ "${BUILD:-1}" = "1" ]; then
  ./gradlew.bat clean bootJar -x test
fi
JAR=build/libs/cursos-0.0.1-SNAPSHOT.jar
[ -f "$JAR" ] || { echo "No existe $JAR"; exit 1; }

SSH_OPTS=(-i "$SSH_KEY" -o StrictHostKeyChecking=accept-new)
DESTINO="$EC2_USER@$EC2_HOST"

ssh "${SSH_OPTS[@]}" "$DESTINO" 'mkdir -p ~/syncpass-deploy'
scp "${SSH_OPTS[@]}" "$JAR" "$DESTINO:syncpass-deploy/syncpass.jar"
scp "${SSH_OPTS[@]}" deploy/syncpass.service deploy/ec2.env.example deploy/instalar-ec2.sh "$DESTINO:syncpass-deploy/"
ssh -t "${SSH_OPTS[@]}" "$DESTINO" 'sudo bash ~/syncpass-deploy/instalar-ec2.sh'
