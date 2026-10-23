Estan definidos en el archivo .env

```
DATABASE_URL=postgres://localhost:5432/dbname
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres
AWS_REGION=us-east-1
AWS_COGNITO_USER_POOL_ID=us-east-1_xxxxx
AWS_COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
AWS_COGNITO_CLIENT_SECRET=

# CORS Configuration
CORS_ENABLED=true
CORS_ALLOWED_ORIGINS=*
CORS_ALLOWED_METHODS=GET,POST,PUT,PATCH,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=*
CORS_EXPOSED_HEADERS=Authorization,Content-Type
CORS_ALLOW_CREDENTIALS=false
CORS_MAX_AGE=3600

# Test user
TEST_USER_EMAIL=test@example.com
TEST_USER_PASSWORD=secret
```

Utilizar `Test user`

- Para validar que la integración con Cognito es correcta
- Validar con curl levantando en localhost