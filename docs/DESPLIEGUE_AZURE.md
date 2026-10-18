# Despliegue en Azure (cuenta Azure for Students)

Parte **Azure** de la arquitectura multi-nube (ver [ARQUITECTURA_MULTINUBE.md](ARQUITECTURA_MULTINUBE.md)).
Cabe en los créditos de **Azure for Students** (se activa con el correo `@duocuc.cl`, sin tarjeta).

| Pieza | Servicio |
|---|---|
| IDaaS | Microsoft Entra External ID |
| API Manager (registro de la API) | Azure API Management (plan Consumption) |
| Backend + clúster RabbitMQ | VM Ubuntu con docker compose |
| Base de datos | Azure Database for PostgreSQL – Flexible Server |
| Frontend | Azure Static Web Apps (plan Free) |

> Para ahorrar créditos, **detén la VM** cuando no la uses.

---

## 1. Grupo de recursos

Portal → *Grupos de recursos* → **Crear** → `rg-pedidos360`, región *Brazil South* o *East US*.

## 2. IDaaS: Microsoft Entra External ID

1. Portal → **Microsoft Entra External ID** → *Crear un inquilino externo* → nombre `pedidos360`,
   subdominio `pedidos360` (queda `pedidos360.ciamlogin.com`), suscripción Azure for Students, grupo `rg-pedidos360`.
2. Cambia al tenant nuevo (ícono de directorios arriba a la derecha).
3. **Registros de aplicaciones → Nuevo registro**
   - Nombre `pedidos360-app` · Cuentas solo de este directorio.
   - URI de redirección, plataforma **Aplicación de página única (SPA)**: `http://localhost:4200/`
     (después agrega la URL pública del frontend, paso 6).
   - Anota **Application (client) ID** y **Directory (tenant) ID**.
4. **Exponer una API** → URI `api://<CLIENT_ID>` → **Agregar un ámbito** `pedidos.access`.
5. **Permisos de API** → *Mis API* → `pedidos360-app` → `pedidos.access` → **Conceder consentimiento de administrador**.
6. **Manifiesto** → en `api`, `"requestedAccessTokenVersion": 2` → Guardar (así el token trae `aud` = CLIENT_ID).
7. **Flujos de usuario** → *Nuevo* → registro e inicio de sesión con *Email con contraseña* → en el flujo,
   *Aplicaciones* → agrega `pedidos360-app`.
8. **Usuarios** → crea 1 o 2 usuarios de prueba. *(Se muestra en la presentación.)*
9. (Opcional) **Roles de aplicación** → rol `Admin`, asignado en *Aplicaciones empresariales*.

Abre `https://pedidos360.ciamlogin.com/<TENANT_ID>/v2.0/.well-known/openid-configuration` y copia:

| Variable (.env del backend) | Valor |
|---|---|
| `ENTRA_ISSUER` | campo `issuer` (normalmente `https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0`) |
| `ENTRA_JWK_SET_URI` | campo `jwks_uri` |
| `ENTRA_CLIENT_ID` | `<CLIENT_ID>` |

## 3. Base de datos: Azure Database for PostgreSQL – Flexible Server

1. Portal → *Azure Database for PostgreSQL* → **Servidor flexible** → Crear: `pedidos360-db`,
   **Burstable B1ms** (incluido en el plan gratuito de 12 meses), usuario `pedidos` + contraseña.
2. **Redes** → acceso público → agrega la IP de la VM (paso 4) y, si el backend también corre en EC2, la de EC2.
3. **Bases de datos** → *Agregar* → `pedidos`.
4. `DB_URL=jdbc:postgresql://pedidos360-db.postgres.database.azure.com:5432/pedidos?sslmode=require`

## 4. VM con Docker (backend + clúster RabbitMQ)

1. *Máquinas virtuales* → Crear → Ubuntu Server 24.04, tamaño **B2s** (4 GB) o B2ms (8 GB), clave SSH.
2. **IP pública estática** (Red → IP pública → Asignación *Estática*): los dos API Managers apuntan a ella.
3. **Reglas de entrada** del NSG:
   | Puerto | Origen | Para qué |
   |---|---|---|
   | 22 | Tu IP | SSH |
   | 8080 | Any (APIM Consumption y API Gateway no tienen IP fija) | Gateway del backend |
   | 15672, 15673 | Tu IP | Consolas RabbitMQ (demo) |
4. Instala Docker y levanta:
   ```bash
   ssh azureuser@<IP_VM>
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && exit        # vuelve a entrar
   git clone https://github.com/<usuario>/pedidos-backend.git && cd pedidos-backend
   cp .env.example .env && nano .env            # DB_URL, ENTRA_*, COGNITO_*, CORS_ORIGENES, claves
   docker compose up -d --build
   docker compose ps
   docker compose exec rabbitmq1 rabbitmq-diagnostics cluster_status
   ```

> Si cambias la Erlang cookie o la configuración del clúster después del primer arranque:
> `docker compose down -v` y vuelve a levantar.

## 5. Registro de la API en Azure API Management

1. Portal → *API Management* → Crear → `pedidos360-apim`, plan **Consumption**.
2. **APIs → Agregar API → OpenAPI** → *Seleccionar un archivo* →
   [infra/api/pedidos360-openapi.yaml](../infra/api/pedidos360-openapi.yaml)
   (antes reemplaza `IP_BACKEND` por la IP de la VM).
   - Nombre `pedidos360`, **sufijo de URL `api`**.
   - Se registran las 31 operaciones (pedidos, documentos, actividad, mensajería y administración de RabbitMQ) con sus
     parámetros y esquemas.
3. En **Configuración** de la API desmarca **Requiere suscripción** (la autenticación la hace el JWT).
4. **All operations → Directiva de entrada (`</>`)** → pega
   [infra/apim/politica-api.xml](../infra/apim/politica-api.xml) reemplazando `ORIGEN_FRONTEND`,
   `SUBDOMINIO`, `TENANT_ID`, `CLIENT_ID` e `IP_VM`. Esta política hace CORS y **validate-jwt** contra Entra.
5. URL para el frontend: `https://pedidos360-apim.azure-api.net` (el frontend agrega `/api/...`).

Prueba:
```bash
curl -i https://pedidos360-apim.azure-api.net/api/pedidos                                # 401 sin token
curl -i -H "Authorization: Bearer abc" https://pedidos360-apim.azure-api.net/api/pedidos  # 401 token inválido
```

## 6. Frontend: Azure Static Web Apps

1. Completa el proveedor `azure` en `public/config.json` del repo `pedidos-frontend` (ver su README) y
   pon `"authHabilitada": true`.
2. Portal → *Static Web Apps* → Crear → plan **Free** → GitHub → repo `pedidos-frontend`
   → preset **Angular**, app location `/`, output `dist/pedidos-frontend/browser`.
3. Agrega la URL `https://<algo>.azurestaticapps.net/` como **URI de redirección SPA** en Entra, en
   `CORS_ORIGENES` del backend y en `ORIGEN_FRONTEND` de la política de APIM.
   Si el mismo frontend también se usa con AWS, agrégala en Cognito (ver guía AWS).
