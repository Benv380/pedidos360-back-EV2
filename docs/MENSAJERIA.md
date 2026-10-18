# Mensajería asíncrona: configuración, métricas y retención

Fundamento técnico de cómo se manejan las colas en Pedidos 360: qué se configuró, cómo leer las métricas,
cómo impactan en rendimiento y estabilidad, y qué políticas evitan el crecimiento descontrolado de mensajes.

---

## 1. Configuración crítica (qué mostrar en pantalla)

| Archivo | Qué contiene | Por qué es crítico |
|---|---|---|
| [`pedidos-common/.../mensajeria.yml`](../pedidos-common/src/main/resources/mensajeria.yml) | Nombres de exchanges/colas/DLQ, ACK manual, `prefetch`, `concurrency`, publisher confirms, reintentos y **retención** | Única fuente de configuración: ningún nombre ni límite está escrito en el código |
| [`TopologiaColas.java`](../pedidos-common/src/main/java/cl/duoc/pedidos/common/mensajeria/config/TopologiaColas.java) | Construye cada cola con `x-dead-letter-exchange`, `x-message-ttl`, `x-max-length`, `x-overflow` | Todas las colas y DLQ quedan con la misma política |
| [`documentos-service/.../RabbitMQConfig.java`](../documentos-service/src/main/java/cl/duoc/pedidos/documentos/config/RabbitMQConfig.java) | Beans `Queue`, `Binding` (fanout + direct) y DLQ | Topología declarada por el consumidor |
| [`ProcesadorMensajes.java`](../pedidos-common/src/main/java/cl/duoc/pedidos/common/mensajeria/consumo/ProcesadorMensajes.java) | `basicAck` / reintento / `basicNack(requeue=false)` | Decide si un mensaje se confirma, se reintenta o va a la DLQ |
| [`infra/rabbitmq/cluster.conf`](../infra/rabbitmq/cluster.conf) | Clúster, muestreo de métricas cada 1 s, límites de memoria y disco del broker | Protección del broker completo |

Extracto de `mensajeria.yml`:

```yaml
listener:
  simple:
    acknowledge-mode: manual   # el consumidor confirma explícitamente
    prefetch: 10               # máx. mensajes en vuelo (sin ACK) por consumidor
    concurrency: 1             # consumidores mínimos por cola...
    max-concurrency: 4         # ...escala hasta 4 si hay backlog
mensajeria:
  retencion:
    colas: { ttl-ms: 86400000, max-mensajes: 10000, desborde: reject-publish-dlx }
    dlq:   { ttl-ms: 604800000, max-mensajes: 5000,  desborde: drop-head }
```

En la consola de RabbitMQ (pestaña *Queues*) la columna **Features** muestra `D TTL Lim DLX DLK`:
durable, TTL, límite de largo, dead-letter exchange y dead-letter routing key.

---

## 2. Métricas: qué significan y cómo impactan

Se ven en el **Monitor de mensajería** del frontend (`GET /api/admin/rabbit/salud` y `/queues`) y en la consola.

| Métrica | Qué es | Valor sano | Impacto en rendimiento | Impacto en estabilidad | Acción |
|---|---|---|---|---|---|
| **Colas activas** | Colas de trabajo con consumidores | Todas con ≥ 1 consumidor | – | Una cola sin consumidor acumula indefinidamente | Revisar el servicio o reanudar el consumidor |
| **Pendientes (Ready)** | Mensajes en espera de entrega: el **backlog** | ≈ 0 o estable | Más backlog = más **latencia** (un pedido espera más su ticket) | Consume memoria/disco del broker; al TTL expiran a la DLQ | Escalar consumidores (`max-concurrency`) |
| **En vuelo (Unacked)** | Entregados y aún sin ACK | ≤ consumidores × prefetch | Prefetch alto = más throughput pero reparto desigual | Si un consumidor muere, sus unacked **vuelven** a la cola (no se pierden) | Ajustar `prefetch` |
| **Consumidores** | Hilos conectados a la cola | 1..4 por cola | Más consumidores = más paralelismo | 0 consumidores = backlog creciente | `concurrency` / instancias |
| **Uso de consumidores** | % del tiempo en que la cola pudo entregar de inmediato | Cerca de 100 % | Bajo = consumidores lentos o prefetch insuficiente | – | Revisar lentitud o prefetch |
| **Tasas publicación / entrega / ACK** | msg/s que entran, salen y se confirman | Publicación ≈ entrega | Publicación > entrega sostenida = backlog creciente | – | Escalar consumidores |
| **Mensajes en DLQ** | Fallidos aislados | 0 | No afectan el flujo normal (están fuera de la cola principal) | Indican errores de negocio o de dependencias | Revisar logs `[NACK -> DLQ]`, reprocesar o purgar |
| **Ocupación** | Mensajes / largo máximo | < 50 % | – | Al 100 % se aplica el desborde: los nuevos van a la DLQ | Escalar o subir el límite con una política |
| **Memoria del nodo** | Uso vs límite (`vm_memory_high_watermark`) | < 60 % | – | Al superarlo, RabbitMQ **bloquea a todos los productores** | Por eso existen TTL y largo máximo |

El monitor transforma estas métricas en **alertas con su impacto**
([`AnalizadorSalud`](../rabbit-admin-service/src/main/java/cl/duoc/pedidos/rabbitadmin/admin/AnalizadorSalud.java)):
cola sin consumidores con pendientes (CRÍTICO), ocupación ≥ 80 % (CRÍTICO) o ≥ 50 % (ADVERTENCIA), publicación
más rápida que el consumo, prefetch saturado, mensajes en DLQ, colas sin retención y nodos caídos o con alarma.

### Parámetros que definen el rendimiento

| Parámetro | Valor | Trade-off |
|---|---|---|
| ACK manual | `manual` | Garantiza que un mensaje solo sale de la cola cuando se procesó (*at-least-once*). Costo: idempotencia obligatoria (`eventoId` único) |
| `prefetch` | 10 | Más alto = más throughput; más bajo = reparto justo y menos memoria en el consumidor |
| `concurrency` / `max-concurrency` | 1 / 4 | Escala hilos bajo carga sin desperdiciar recursos en reposo |
| Mensajes persistentes + colas durables | sí | Sobreviven a un reinicio del broker. Costo: escritura en disco |
| Publisher confirms | `correlated` | El productor sabe si el broker aceptó cada mensaje (`[CONFIRM]` en logs) |
| Reintentos | 3 con backoff lineal (1 s, 2 s, 3 s) | Absorbe fallos transitorios sin saturar al servicio caído. El hilo espera, por eso hay hasta 4 consumidores |
| `requeue=false` | siempre | Evita el bucle infinito de un mensaje "venenoso" que nunca se podrá procesar |

---

## 3. Retención y limpieza: cómo se evita el crecimiento descontrolado

Ciclo de vida de un mensaje:

```
publicado ──► cola de trabajo (TTL 24 h, máx. 10.000, reject-publish-dlx)
                 │
                 ├─ procesado OK ─────────────────────────► ACK: se elimina
                 ├─ error no recuperable ─────────────────► NACK ──┐
                 ├─ error recuperable ×3 ─────────────────► NACK ──┤
                 ├─ nadie lo consume en 24 h (expired) ───────────┤ pedidos.dlx
                 └─ llega con la cola llena (maxlen) ─────────────┘
                                                                   ▼
                              DLQ (TTL 7 días, máx. 5.000, drop-head)
                                 ├─ reprocesar ──► vuelve a su cola de origen
                                 ├─ purgar ──────► se elimina
                                 ├─ cumple 7 días ► se ELIMINA (la DLQ no tiene DLX)
                                 └─ DLQ llena ───► se descarta el fallo más antiguo
```

| Mecanismo | Dónde | Valor | Qué evita |
|---|---|---|---|
| **TTL de mensajes** (`x-message-ttl`) en colas de trabajo | `mensajeria.retencion.colas.ttl-ms` | 24 h | Mensajes "olvidados" acumulándose si un consumidor está caído. Al expirar **no se pierden**: pasan a la DLQ con motivo `expired` |
| **Largo máximo** (`x-max-length`) | `...colas.max-mensajes` | 10.000 | Que una caída prolongada de consumidores llene la memoria del broker |
| **Desborde** (`x-overflow`) en colas de trabajo | `...colas.desborde` | `reject-publish-dlx` | Al llenarse, el mensaje nuevo se desvía a la DLQ en vez de perderse o de expulsar a otros |
| **TTL de DLQ** | `mensajeria.retencion.dlq.ttl-ms` | 7 días | Que los fallidos se acumulen para siempre: se conservan 7 días para análisis y luego se eliminan |
| **Largo máximo + `drop-head` en DLQ** | `...dlq.*` | 5.000 | Si hay una avalancha de errores, se conserva lo más reciente |
| **Expiración de colas** (`x-expires`) | Administrador al crear colas | opcional | Colas temporales o de prueba que quedan abandonadas |
| **Políticas** (en caliente) | `PUT /api/admin/rabbit/policies/{nombre}` | por patrón | Ajustar límites sin recrear colas (los argumentos `x-*` son inmutables). Para TTL y largo rige el menor entre argumento y política |
| **Purga y reproceso** | `DELETE .../queues/{n}/messages`, `POST .../queues/{dlq}/reprocesar` | manual | Limpieza o recuperación operativa de la DLQ |
| **Límites del broker** | `cluster.conf` | memoria 60 %, disco 1 GB | Última barrera: bloquea a los productores antes de que el nodo se caiga |

### Cómo se dimensionaron los valores

- **24 h** de TTL: un día cubre una caída del consumidor durante la noche o un fin de semana con alertas. Un
  ticket o una notificación con más de un día de atraso ya no aporta valor y conviene revisarlo en la DLQ.
- **10.000** mensajes: con mensajes de ~1 KB son ~10 MB por cola, muy por debajo del límite de memoria del broker.
- **7 días** en DLQ: una semana laboral para analizar y reprocesar.
- **`reject-publish-dlx`** en colas de trabajo (y no `drop-head`): en pedidos no se quiere perder un evento
  en silencio. `reject-publish` también lo evitaría, pero obligaría al productor a manejar el NACK.

> Los argumentos `x-*` son inmutables: cambiar un valor de `retencion` en una cola ya existente produce
> `PRECONDITION_FAILED`. En producción se usa una **política**; en el entorno de la evaluación basta con
> `docker compose down -v` para recrear las colas.
