# Guion de presentación · foco en mensajería asíncrona (8–10 min)

Según las indicaciones del docente, el foco es la **mensajería**, no API Manager ni IDaaS. Fundamento
técnico de cada punto: [MENSAJERIA.md](MENSAJERIA.md).

**Antes de empezar**, ten abiertas estas pestañas:
1. El IDE con `mensajeria.yml`, `TopologiaColas.java`, `RabbitMQConfig.java` (documentos) y `ProcesadorMensajes.java`.
2. La app → **Mensajería** (monitor; se actualiza cada 3 s).
3. La consola RabbitMQ `http://<IP>:15672` → *Queues*.
4. Una terminal con `docker compose logs -f documentos-service notificaciones-service`.

Deja todas las DLQ en 0 (botón *Purgar*) y elimina `demo.queue` si existe.

---

## 1 · Contexto (30 s)

*"Pedidos 360 desacopla con RabbitMQ: pedidos-service publica eventos en exchanges fanout, topic y direct, y
tres colas los consumen (notificaciones, documentos y auditoría), cada una con su DLQ, en un clúster de 2 nodos."*
Muestra la topología en la consola (*Exchanges → pedidos.eventos.fanout → Bindings*).

## 2 · Configuración crítica en vivo (2 min)  → exigencia 1

| Mostrar | Decir |
|---|---|
| `mensajeria.yml` → `acknowledge-mode: manual` | "Un mensaje solo sale de la cola cuando el consumidor confirma. Si el servicio muere, los no confirmados vuelven a la cola." |
| `prefetch: 10`, `concurrency 1 / max 4` | "Prefetch limita los mensajes en vuelo por consumidor: equilibrio entre throughput y reparto justo. La concurrencia escala hasta 4 hilos con backlog." |
| `mensajeria.retencion` | "Toda cola tiene TTL, largo máximo y comportamiento de desborde. Esto evita el crecimiento descontrolado." |
| `TopologiaColas.colaTrabajo()` | "Aquí se traducen a argumentos `x-message-ttl`, `x-max-length`, `x-overflow` y `x-dead-letter-exchange`." |
| `ProcesadorMensajes` (tabla del Javadoc) | "ACK si sale bien; error recuperable: 3 reintentos con backoff; error permanente o reintentos agotados: NACK sin requeue y va a la DLQ. Nunca `requeue=true`, para evitar bucles infinitos." |
| Consola → *Queues* → columna **Features** `D TTL Lim DLX DLK`; abrir `documentos.queue` → *Arguments* | "Así quedan aplicados en el broker." |

## 3 · Colas activas, pendientes y consumidores (2 min)  → exigencia 2

1. Monitor → tarjetas: **colas activas, pendientes, en vuelo, consumidores, en DLQ, tasas, nodos**.
   *"Hoy todo está en 0 pendientes: los consumidores procesan al ritmo en que se publica."*
2. **Consumidores → Pausar `documentos`**.
3. Pestaña **Pedidos** → crea 3 o 4 pedidos. Vuelve a **Mensajería**:
   - `documentos.queue`: **Pendientes** sube, **Cons.** = 0.
   - Alerta **CRÍTICO**: *"Sin consumidores con N mensajes pendientes"*.
   - *"Impacto en rendimiento: aumenta la latencia, el cliente no recibe su ticket. Impacto en estabilidad: el
     backlog ocupa memoria del broker; si sigue creciendo, al TTL de 24 h esos mensajes pasarían a la DLQ y al
     llegar a 10.000 los nuevos también. Si la memoria superara el 60 %, RabbitMQ bloquearía a TODOS los
     productores. Por eso cada cola tiene límites."*
   - Notificaciones y auditoría siguen funcionando: *"el fanout entregó una copia a cada cola; un consumidor
     caído no afecta a los demás."*
4. **Reanudar** → los pendientes bajan a 0, se ven las **tasas de entrega y ACK** y aparecen los tickets en Documentos.

## 4 · DLQ (1,5 min)  → exigencia 2

1. Pedidos → crea un pedido con *Simular fallo* = **Documento** (error recuperable) y otro con **Notificación** (no recuperable).
2. En la terminal: `[RETRY] intento 1/4 … 2/4 … 3/4` y luego `[NACK -> DLQ]`; para notificación, `[NACK -> DLQ]` inmediato.
3. Monitor: `documentos.dlq` y `notificaciones.dlq` en 1, con alerta ADVERTENCIA.
   *"La DLQ aísla los mensajes fallidos: la cola principal sigue fluyendo. Se conservan 7 días para analizarlos
   (la cabecera x-death indica cola, motivo y cantidad de veces) y luego se eliminan solos."*
4. **Reprocesar** (vuelven a su cola y, como el fallo es simulado, regresan a la DLQ) y luego **Purgar**.

## 5 · Retención y limpieza en vivo (2,5 min)  → exigencia 3

1. **Crear cola**: `demo.queue`, TTL `15000`, largo máximo `5`, desborde `reject-publish-dlx`, con DLQ.
   Mostrar en la tabla: TTL 15 s, desborde y origen "argumentos de la cola".
2. Presiona **+3 msgs** tres veces (9 mensajes):
   - `demo.queue` = **5/5 (100 %)**, alerta CRÍTICO de ocupación.
   - `demo.queue.dlq` = **4**: *"al llenarse, los nuevos se desvían a la DLQ (motivo maxlen); no se pierden."*
3. Espera 15 s: `demo.queue` baja a **0** y la DLQ sube a **9**: *"expiraron por TTL (motivo expired)."*
4. **Política en caliente**: nombre `limite-demo`, patrón `^demo\.`, largo máximo `2`, desborde `drop-head` → *Aplicar*.
   Presiona **+3 msgs**: quedan **2**. *"Las políticas cambian los límites sin recrear la cola; los argumentos
   x-* son inmutables. Para TTL y largo rige el menor entre argumento y política."*
5. Cierre: *"Capas de retención: TTL y largo máximo en colas de trabajo (24 h / 10.000); TTL y drop-head en DLQ
   (7 días / 5.000); expiración de colas sin uso; políticas por patrón; purga y reproceso manual; y como última
   barrera, los límites de memoria y disco del broker."*
6. Elimina la política y `demo.queue` / `demo.queue.dlq`.

## 6 · Clúster (30 s)

Monitor → *Clúster RabbitMQ*: 2 nodos activos, memoria vs límite.
`docker compose exec rabbitmq1 rabbitmq-diagnostics cluster_status`.

---

## Preguntas probables

- **¿Qué pasa si un consumidor muere con mensajes sin ACK?** El canal se cierra y RabbitMQ re-encola esos
  mensajes (`redelivered=true`). Por eso los consumidores son idempotentes (`eventoId` único).
- **¿Por qué prefetch 10 y no 1 o 1000?** Con 1, el consumidor espera un viaje de red por mensaje (bajo
  throughput); con 1000, un consumidor lento acapara mensajes que otro podría procesar y sube la memoria. 10
  equilibra ambos para procesamientos de milisegundos.
- **¿TTL en la DLQ no hace perder información?** Los fallos quedan registrados en logs (`[NACK -> DLQ]` con
  motivo) y 7 días es la ventana operativa para reprocesar. Sin TTL, la DLQ crecería sin límite.
- **¿Por qué `reject-publish-dlx` y no `drop-head` en las colas de trabajo?** `drop-head` descartaría pedidos
  antiguos que siguen siendo válidos. Con `reject-publish-dlx` el exceso queda en la DLQ y se puede reprocesar.
- **¿Cómo escalarían?** Subir `max-concurrency` o levantar más réplicas del consumidor (*competing consumers*
  sobre la misma cola). El monitor alerta cuando la publicación supera al consumo.
- **¿Qué pasa si se cae un nodo?** Los servicios conocen ambos nodos y reconectan. Las colas clásicas viven en
  un nodo (columna *Nodo*): si cae ese nodo, no están disponibles hasta que vuelva. La replicación real requiere
  colas quorum con 3 nodos o más.
