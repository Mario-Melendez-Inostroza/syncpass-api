#!/usr/bin/env bash
# Se ejecuta en AWS CloudShell (ya trae el AWS CLI y tus credenciales). Crea el HTTP API de SyncPass:
#   - Públicas (sin token): GET /api/health y el login, refresh y recuperación de contraseña.
#   - Todo lo demás bajo /api/** exige un access token de Cognito (authorizer JWT).
#   - /api/auth/registro-asistente SÍ es pública (el rol lo fija el servidor y solo concede ASISTENTE).
#   - /api/auth/registro NO se publica sin token: sin Authorization, API Gateway responde 401.
# Uso:  BACKEND=http://<ip-elastica>:8080 bash crear-api-gateway.sh
REGION=us-east-1
POOL=us-east-1_NrSiP3RaR
CLIENT=6qetsn76s4avholcr1s3ne65h5
BACKEND="${BACKEND:?Define BACKEND, por ejemplo http://1.2.3.4:8080}"

API=$(aws apigatewayv2 create-api --region $REGION --name syncpass-api --protocol-type HTTP \
  --cors-configuration '{"AllowOrigins":["*"],"AllowMethods":["GET","POST","PUT","PATCH","DELETE","OPTIONS"],"AllowHeaders":["Authorization","Content-Type"],"MaxAge":3600}' \
  --query ApiId --output text) || exit 1

integ() { aws apigatewayv2 create-integration --region $REGION --api-id $API --integration-type HTTP_PROXY \
  --integration-method "$2" --integration-uri "$1" --payload-format-version 1.0 --query IntegrationId --output text; }

AUTH=$(aws apigatewayv2 create-authorizer --region $REGION --api-id $API --name cognito --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Audience=$CLIENT,Issuer=https://cognito-idp.$REGION.amazonaws.com/$POOL" \
  --query AuthorizerId --output text) || exit 1

pub() { I=$(integ "$BACKEND$2" "$1"); aws apigatewayv2 create-route --region $REGION --api-id $API \
  --route-key "$1 $2" --target integrations/$I --authorization-type NONE >/dev/null && echo "publica: $1 $2"; }
pub GET /api/health
pub POST /api/auth/login
pub POST /api/auth/registro-asistente   # el asistente se registra solo; el servidor fija su rol (ASISTENTE)
pub POST /api/auth/refresh
pub POST /api/auth/olvide-contrasena/solicitar
pub POST /api/auth/olvide-contrasena/confirmar

I=$(integ "$BACKEND/api/{proxy}" ANY)
aws apigatewayv2 create-route --region $REGION --api-id $API --route-key 'ANY /api/{proxy+}' \
  --target integrations/$I --authorization-type JWT --authorizer-id $AUTH >/dev/null && echo "protegida: ANY /api/{proxy+}"
aws apigatewayv2 create-stage --region $REGION --api-id $API --stage-name '$default' --auto-deploy >/dev/null
echo "API_ID=$API"
echo "URL=https://$API.execute-api.$REGION.amazonaws.com"
# Preflight CORS: sin esta ruta (sin authorizer) el OPTIONS del navegador recibe 401 y la web no puede llamar a la API.
I2=$(aws apigatewayv2 get-integrations --region $REGION --api-id $API --query "Items[?ends_with(IntegrationUri,'/api/{proxy}')].IntegrationId" --output text)
aws apigatewayv2 create-route --region $REGION --api-id $API --route-key 'OPTIONS /api/{proxy+}' --target integrations/$I2 --authorization-type NONE >/dev/null && echo "publica: OPTIONS /api/{proxy+}"
