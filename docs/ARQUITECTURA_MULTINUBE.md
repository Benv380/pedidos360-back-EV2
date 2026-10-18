# Arquitectura multi-nube (Azure + AWS)

El mismo frontend y el mismo backend se exponen por **dos rutas independientes**, una por nube. Cada nube
aporta su propio **IDaaS** y su propio **API Manager**, y ambas registran la **misma API** a partir de un
único contrato OpenAPI.

```
                               ┌──────────────── AZURE ────────────────┐
                               │  Microsoft Entra External ID (IDaaS)  │
                  login OIDC   │        │ JWT (aud = client id)        │
                 ┌────────────►│        ▼                              │
                 │             │  Azure API Management                 │
                 │             │   · API importada desde OpenAPI       │
                 │             │   · validate-jwt (Entra) + CORS       │──┐
   Frontend      │             └───────────────────────────────────────┘  │
   Angular  ─────┤                                                        │  http://IP_BACKEND:8080/api/...
   (Static Web   │             ┌───────────────── AWS ─────────────────┐  │
    Apps o       │  login OIDC │  Amazon Cognito (IDaaS)               │  │
    Amplify)     └────────────►│        │ JWT (client_id)              │  │
                               │        ▼                              │  │
                               │  Amazon API Gateway (HTTP API)        │  │
                               │   · API importada desde OpenAPI       │──┤
                               │   · JWT authorizer (Cognito) + CORS   │  │
                               └───────────────────────────────────────┘  │
                                                                          ▼
                                  VM (Azure) o EC2 (AWS) con docker compose
                                  ├─ gateway nginx :8080
                                  ├─ pedidos-service ──► RabbitMQ (fanout / topic / direct)
                                  ├─ notificaciones-service, documentos-service (consumidores + DLQ)
                                  ├─ rabbit-admin-service
                                  ├─ rabbitmq1 ◄── clúster ──► rabbitmq2
                                  └─► PostgreSQL (Azure Flexible Server o RDS)
```

## Cómo encaja cada requisito

| Requisito | Azure | AWS |
|---|---|---|
| Tenant IDaaS + usuarios | Entra External ID (tenant externo, usuarios) | Cognito (User Pool, usuarios) |
| Login OAuth 2.0 / OIDC con JWT | Code + PKCE contra `ciamlogin.com` | Code + PKCE contra el dominio Cognito |
| Registro de la API en el API Manager | Import de `pedidos360-openapi.yaml` en APIM | Import de `pedidos360-openapi-aws.yaml` en API Gateway |
| API Manager valida JWT | Política `validate-jwt` | JWT authorizer |
| Backend valida JWT | Acepta tokens de **ambos** IDaaS (multi-issuer, ver `seguridad.yml`) | ← mismo backend |
| Backend desplegado | VM + docker compose | (opcional) EC2 + docker compose |
| Frontend desplegado | Static Web Apps | (opcional) Amplify |

## Defensa en profundidad del JWT

1. El **API Manager** de cada nube rechaza (401) lo que no venga firmado por su IDaaS.
2. Cada **microservicio** vuelve a validar el token: elige el proveedor por el claim `iss`, verifica la
   firma con el JWKS de ese IDaaS, la expiración y la audiencia (`aud` en Entra, `client_id` en Cognito).
   Un token de un emisor no configurado se rechaza con 401.

## Guías

1. [DESPLIEGUE_AZURE.md](DESPLIEGUE_AZURE.md): Entra, PostgreSQL, VM con docker compose, APIM y Static Web Apps.
2. [DESPLIEGUE_AWS.md](DESPLIEGUE_AWS.md): Cognito, API Gateway y, opcionalmente, EC2 y Amplify.
3. [GUIA_PRESENTACION.md](GUIA_PRESENTACION.md): guion de la demo.
