# Pedidos 360 · Backend (Cloud Native + RabbitMQ)

Microservicios **Java 21 / Spring Boot 3.5** que gestionan pedidos y procesan de forma **asíncrona** las
notificaciones, la generación de documentos (ticket y boleta) y la auditoría mediante un **clúster RabbitMQ
de 2 nodos**. Usa exchanges **fanout**, **topic** y **direct**, una **DLQ por cola** y ACK manual.

| Módulo | Puerto | Rol |
|---|---|---|
| `pedidos-common` | – | Librería compartida: **nombres centralizados** (`mensajeria.yml`), exchanges, ACK/NACK/DLQ (`ProcesadorMensajes`), seguridad JWT y errores REST |
| `pedidos-service` | 8081 | CRUD de pedidos (PostgreSQL) y **PRODUCTOR** de eventos |
| `notificaciones-service` | 8082 | **CONSUMIDOR** de `notificaciones.queue` y `auditoria.queue` |
| `documentos-service` | 8083 | **CONSUMIDOR** de `documentos.queue` (tickets y boletas) |
| `rabbit-admin-service` | 8084 | **Administrador** de RabbitMQ: API REST para colas, exchanges y bindings |
| `gateway` (nginx) | 8080 | Entrada única que usa Azure API Management como backend |

## Topología de mensajería

```
                         ┌──────────────── FANOUT: pedidos.eventos.fanout ─────────────────┐
 pedidos-service         │  PEDIDO_CREADO se difunde a TODAS las colas enlazadas            │
 (productor)  ──────────►├──► notificaciones.queue ─► notificaciones-service (notificación)  │
                         ├──► documentos.queue     ─► documentos-service (TICKET)            │
                         └──► auditoria.queue      ─► notificaciones-service (auditoría)     │
              ──────────► TOPIC: pedidos.estados.topic   rk = pedido.estado.<estado>
                            "pedido.estado.*" ─► notificaciones.queue
                            "pedido.#"        ─► auditoria.queue
              ──────────► DIRECT: pedidos.documentos.direct   rk = documento.boleta
                            "documento.boleta" ─► documentos.queue (BOLETA)
                            "documento.boleta" ─► auditoria.queue

 Cada cola tiene x-dead-letter-exchange = pedidos.dlx (DIRECT):
   notificaciones.queue ─NACK─► pedidos.dlx ─► notificaciones.dlq
   documentos.queue     ─NACK─► pedidos.dlx ─► documentos.dlq
   auditoria.queue      ─NACK─► pedidos.dlx ─► auditoria.dlq
```

### ¿Por qué fanout?
Crear un pedido es un **evento de negocio que interesa a varios consumidores independientes** (notificar,
emitir ticket, auditar). Con un exchange **fanout** el productor publica una sola vez, sin routing key, y
RabbitMQ entrega una copia a cada cola enlazada. Agregar un nuevo interesado (ej. analítica) es solo crear
una cola y enlazarla, sin tocar `pedidos-service`. Un consumidor que falla no afecta a los demás: cada copia
se confirma o se envía a su propia DLQ.

## Manejo de ACK, reintentos y DLQ

Todos los consumidores usan `acknowledge-mode: manual` y delegan en
[`ProcesadorMensajes`](pedidos-common/src/main/java/cl/duoc/pedidos/common/mensajeria/consumo/ProcesadorMensajes.java):

| Resultado | Decisión |
|---|---|
| Procesado OK | `basicAck` |
| JSON inválido, `ErrorNoRecuperableException` u otro error | `basicNack(requeue=false)` → DLQ inmediata |
| `ErrorRecuperableException` (intento ≤ 3) | Se re-publica con cabecera `x-reintentos+1` (backoff lineal) y `basicAck` del original |
| `ErrorRecuperableException` (reintentos agotados) | `basicNack(requeue=false)` → DLQ |

Nunca se hace `requeue=true` ante errores de negocio, para evitar bucles infinitos. Cada decisión queda en
el log con las etiquetas `[ACK]`, `[RETRY]` y `[NACK -> DLQ]`. Los consumidores son **idempotentes**
(`eventoId` único), así un reintento no duplica notificaciones ni documentos.

## Retención y limpieza

Ninguna cola puede crecer sin control. Detalle, métricas e impacto en [docs/MENSAJERIA.md](docs/MENSAJERIA.md).

| | Colas de trabajo | DLQ |
|---|---|---|
| TTL de mensajes (`x-message-ttl`) | 24 h → al expirar pasan a la DLQ | 7 días → se eliminan |
| Largo máximo (`x-max-length`) | 10.000 | 5.000 |
| Desborde (`x-overflow`) | `reject-publish-dlx`: el nuevo va a la DLQ | `drop-head`: se descarta el más antiguo |

Además: políticas de RabbitMQ aplicables en caliente, expiración de colas sin uso (`x-expires`), purga y
reproceso de DLQ desde el administrador, y límites de memoria y disco del broker (`infra/rabbitmq/cluster.conf`).
Todos los valores están en `mensajeria.yml` (`mensajeria.retencion`).

**Demo de fallos:** al crear un pedido se puede enviar `"simularFallo"`:
`NOTIFICACION` (no recuperable → `notificaciones.dlq`), `DOCUMENTO` (recuperable → 3 reintentos →
`documentos.dlq`) o `AUDITORIA` (→ `auditoria.dlq`).

## API REST

### Pedidos (`pedidos-service`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/pedidos` | Listar |
| GET | `/api/pedidos/{id}` | Detalle |
| POST | `/api/pedidos` | Crear → publica en **fanout** |
| PATCH | `/api/pedidos/{id}/estado` | Cambiar estado → publica en **topic** |
| POST | `/api/pedidos/{id}/boleta` | Solicitar boleta → publica en **direct** (202) |

`GET /api/notificaciones`, `GET /api/auditoria`, `GET /api/documentos`, `GET /api/documentos/{id}/contenido`

Control de consumidores (pausar/reanudar un `@RabbitListener` para observar el backlog):
`GET /api/{notificaciones|documentos}/consumidores` y `POST /api/{servicio}/consumidores/{id}/{pausar|reanudar}`.

### Administrador RabbitMQ (`rabbit-admin-service`)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/admin/rabbit/salud` | Totales, tasas y **alertas con su impacto** |
| GET | `/api/admin/rabbit/nodes` | Nodos del clúster (memoria y alarmas) |
| POST | `/api/admin/rabbit/queues/{nombre}/messages` | Publicar mensajes de prueba (colas fuera de la base) |
| POST | `/api/admin/rabbit/queues/{dlq}/reprocesar?max=` | Devolver mensajes de una DLQ a su cola |
| GET / PUT / DELETE | `/api/admin/rabbit/policies[/{nombre}]` | Políticas de retención en caliente |
| GET | `/api/admin/rabbit/queues` | Listar colas |
| GET | `/api/admin/rabbit/queues/{nombre}` | Detalle |
| POST | `/api/admin/rabbit/queues` | Crear (`tipo` CLASSIC/QUORUM, `conDlq`, `ttlMs`, `maxMensajes`) |
| DELETE | `/api/admin/rabbit/queues/{nombre}` | Eliminar (`?soloSiVacia=true`) |
| DELETE | `/api/admin/rabbit/queues/{nombre}/messages` | Purgar |
| GET / POST | `/api/admin/rabbit/exchanges` | Listar / crear (DIRECT, TOPIC, FANOUT) |
| DELETE | `/api/admin/rabbit/exchanges/{nombre}` | Eliminar |
| GET / POST | `/api/admin/rabbit/bindings` | Listar / crear |
| DELETE | `/api/admin/rabbit/bindings?exchange=&cola=&routingKey=` | Eliminar |

Toda la lógica está en
[`RabbitAdminService`](rabbit-admin-service/src/main/java/cl/duoc/pedidos/rabbitadmin/admin/RabbitAdminService.java).
Validaciones: DTO con Bean Validation (nombre obligatorio, 3-100 caracteres, solo `[a-zA-Z0-9._-]`, TTL y
largo máximo en rango) más [`ValidadorTopologia`](rabbit-admin-service/src/main/java/cl/duoc/pedidos/rabbitadmin/admin/ValidadorTopologia.java)
(prefijo `amq.` reservado, QUORUM debe ser durable, routing key según tipo de exchange). La topología base
está protegida y no se puede eliminar (409). Los errores se devuelven como `ProblemDetail` con mensajes claros.

## Multi-nube: Azure + AWS

| | Azure | AWS |
|---|---|---|
| IDaaS | Microsoft Entra External ID | Amazon Cognito |
| API Manager | Azure API Management | Amazon API Gateway (HTTP API) |
| Registro de la API | [infra/api/pedidos360-openapi.yaml](infra/api/pedidos360-openapi.yaml) | [infra/api/pedidos360-openapi-aws.yaml](infra/api/pedidos360-openapi-aws.yaml) (generado) |
| Validación JWT en el API Manager | [validate-jwt](infra/apim/politica-api.xml) | JWT authorizer |

Detalle en [docs/ARQUITECTURA_MULTINUBE.md](docs/ARQUITECTURA_MULTINUBE.md).

## Seguridad

Cada microservicio es un **OAuth 2.0 Resource Server multi-IDaaS**: el filtro `BearerTokenAuthenticationFilter`
toma el JWT, elige el proveedor según el claim `iss` (Entra o Cognito, configurados en
[seguridad.yml](pedidos-common/src/main/resources/seguridad.yml)) y valida firma (JWKS), `exp`, `iss` y
audiencia (`aud` en Entra, `client_id` en Cognito) antes de llegar al controlador. Además, el API Manager
de cada nube valida el mismo token.

## Ejecutar en local

Requisitos: Docker Desktop. Opcional: JDK 21 para correr desde el IDE.

```bash
cp .env.example .env        # en local puedes dejar SEGURIDAD_HABILITADA=false
docker compose up -d --build
docker compose exec rabbitmq1 rabbitmq-diagnostics cluster_status
```

- Gateway: http://localhost:8080/api/pedidos
- Consola RabbitMQ: http://localhost:15672 (nodo 1) y http://localhost:15673 (nodo 2), usuario de `.env`

Pruebas unitarias:

```bash
./mvnw test
```

## Despliegue

- Azure: [docs/DESPLIEGUE_AZURE.md](docs/DESPLIEGUE_AZURE.md)
- AWS: [docs/DESPLIEGUE_AWS.md](docs/DESPLIEGUE_AWS.md)
- Guion de la presentación (foco en mensajería): [docs/GUIA_PRESENTACION.md](docs/GUIA_PRESENTACION.md)
- Fundamento técnico de colas, métricas y retención: [docs/MENSAJERIA.md](docs/MENSAJERIA.md)
