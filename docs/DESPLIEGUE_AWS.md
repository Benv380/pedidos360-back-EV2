# Despliegue en AWS

Parte **AWS** de la arquitectura multi-nube (ver [ARQUITECTURA_MULTINUBE.md](ARQUITECTURA_MULTINUBE.md)).
Sirve con una cuenta AWS normal (capa gratuita) o con **AWS Academy Learner Lab**.

| Pieza | Servicio |
|---|---|
| IDaaS | Amazon Cognito (User Pool) |
| API Manager (registro de la API) | Amazon API Gateway – HTTP API con **JWT authorizer** |
| Backend + clúster RabbitMQ (opcional, segundo despliegue) | EC2 con docker compose |
| Frontend (opcional) | AWS Amplify Hosting |

> **Learner Lab:** la sesión dura ~4 h y al terminar se **detienen** las instancias EC2 (los recursos
> no se borran). Al volver a iniciar el lab, EC2 arranca de nuevo. Usa una **Elastic IP** para que la IP
> no cambie. Si Cognito aparece bloqueado en tu lab, consúltalo con el docente.
> Usa la región **us-east-1** (la habitual en Learner Lab).

---

## 1. IDaaS: Amazon Cognito

1. Consola → **Cognito** → *User pools* → **Create user pool**.
   - Tipo de aplicación: **Single-page application (SPA)** · nombre `pedidos360-spa`.
   - Opciones de inicio de sesión: **Email** · permitir auto-registro (opcional).
   - **Return URL**: `http://localhost:4200/`
2. En el user pool:
   - **App clients → pedidos360-spa → Login pages → Edit**: *Allowed callback URLs*
     `http://localhost:4200/` y `https://<url-frontend>/`; *Allowed sign-out URLs* las mismas;
     *OAuth grant type* **Authorization code grant**; *Scopes* `openid`, `email`, `profile`.
     (El cliente SPA no tiene secreto: usa PKCE.)
   - **Domain**: crea un dominio Cognito `pedidos360-<tu-nombre>` →
     `https://pedidos360-<tu-nombre>.auth.us-east-1.amazoncognito.com`.
   - **Users → Create user**: 1 o 2 usuarios de prueba (email + contraseña). *(Se muestra en la presentación.)*
   - (Opcional) **Groups → Create group** `Admin` y agrega tu usuario. Con `ROL_ADMIN=Admin` el backend lo exige
     para `/api/admin/**` (claim `cognito:groups`).
3. Anota **User pool ID** (`us-east-1_XXXX`) y **Client ID**.

| Variable (.env del backend) | Valor |
|---|---|
| `COGNITO_ISSUER` | `https://cognito-idp.us-east-1.amazonaws.com/<USER_POOL_ID>` |
| `COGNITO_CLIENT_ID` | `<CLIENT_ID>` (el access token de Cognito lo trae en el claim `client_id`) |

Después de editar `.env` en la VM: `docker compose up -d` (recrea los servicios con las variables nuevas).

## 2. Registro de la API en API Gateway (HTTP API)

El archivo [infra/api/pedidos360-openapi-aws.yaml](../infra/api/pedidos360-openapi-aws.yaml) se generó desde
el mismo contrato que usa Azure e incluye las extensiones de AWS: integración `HTTP_PROXY` por ruta,
**JWT authorizer** de Cognito y CORS.

1. Reemplaza en el archivo:
   `IP_BACKEND` (IP pública de la VM de Azure o Elastic IP de EC2), `REGION` (`us-east-1`), `USER_POOL_ID`,
   `CLIENT_ID_COGNITO`, `ORIGEN_FRONTEND`.
2. Consola → **API Gateway** → *Create API* → **HTTP API → Import** → sube el archivo → *Import*.
3. Revisa:
   - **Routes**: 31 operaciones (`GET /api/pedidos`, `POST /api/pedidos`, `PATCH /api/pedidos/{id}/estado`, …)
     cada una con el autorizador `cognito`.
   - **Authorization → cognito**: tipo JWT, issuer y audience de Cognito.
   - **CORS**: orígenes del frontend.
4. **Deploy**: usa la etapa `$default` con *auto-deploy*. URL: `https://<API_ID>.execute-api.us-east-1.amazonaws.com`.

Prueba:
```bash
curl -i https://<API_ID>.execute-api.us-east-1.amazonaws.com/api/pedidos                                # 401
curl -i -H "Authorization: Bearer abc" https://<API_ID>.execute-api.us-east-1.amazonaws.com/api/pedidos  # 401
```

> Si alguna ruta con parámetro (`/{id}`, `/{nombre}`) responde 404 desde el backend, revisa en
> *Integrations* que la URL tenga el mismo `{parametro}`. Alternativa: crear una ruta `ANY /api/{proxy+}`
> con integración `http://IP_BACKEND:8080/api/{proxy}` y el mismo autorizador.

Si modificas el contrato base, regenera la versión AWS:
```bash
pip install pyyaml
python infra/api/generar_openapi_aws.py
```

## 3. (Opcional) Backend también en EC2

Si el docente pide el backend desplegado en AWS además de Azure:

1. EC2 → *Launch instance* → Ubuntu 24.04, **t3.medium** (4 GB), par de claves, *Security group*:
   22 (tu IP), 8080 (Anywhere), 15672-15673 (tu IP). Asocia una **Elastic IP**.
2. Mismos pasos que en la VM de Azure: instalar Docker, clonar, `.env`, `docker compose up -d --build`.
   - Base de datos: la misma de Azure (agrega la Elastic IP al firewall de PostgreSQL) o una **RDS PostgreSQL**
     `db.t3.micro` (`DB_URL=jdbc:postgresql://<endpoint-rds>:5432/pedidos`).
3. Apunta `IP_BACKEND` del archivo de AWS a la Elastic IP para que API Gateway use el backend de AWS.

## 4. (Opcional) Frontend en AWS Amplify

Consola → **Amplify** → *Host web app* → GitHub `pedidos-frontend` → build `npm ci && npm run build`,
directorio de salida `dist/pedidos-frontend/browser` → agrega una regla de reescritura
`</^[^.]+$|\.(?!(css|js|json|ico|png|svg|txt)$)([^.]+$)/>` → `/index.html` (200).
Agrega la URL `https://main.<id>.amplifyapp.com/` en Cognito, Entra, `CORS_ORIGENES` y en el CORS de ambos API Managers.
