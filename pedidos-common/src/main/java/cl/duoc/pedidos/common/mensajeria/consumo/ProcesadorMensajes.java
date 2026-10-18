package cl.duoc.pedidos.common.mensajeria.consumo;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Manejo explícito de la confirmación de mensajes para TODOS los consumidores
 * (los listeners trabajan con acknowledge-mode: manual).
 *
 * <pre>
 *  Resultado de la lógica de negocio        Decisión
 *  ─────────────────────────────────────    ──────────────────────────────────────────────────────
 *  OK                                       basicAck                      -> mensaje eliminado de la cola
 *  JSON inválido / tipo desconocido         basicNack(requeue=false)      -> DLQ inmediata (no recuperable)
 *  ErrorNoRecuperableException / otros      basicNack(requeue=false)      -> DLQ inmediata
 *  ErrorRecuperableException, intento < N   republica con x-reintentos+1  -> basicAck del original (reintento)
 *  ErrorRecuperableException, intento = N   basicNack(requeue=false)      -> DLQ (reintentos agotados)
 * </pre>
 *
 * Nunca se usa requeue=true para errores de negocio: eso provocaría un bucle infinito de reprocesos.
 * El reintento se hace re-publicando el mensaje con un contador en la cabecera, así se sabe
 * cuántas veces se intentó y el broker no lo reentrega sin control.
 * Al hacer NACK el broker lo envía al dead-letter exchange configurado en la cola (x-dead-letter-exchange)
 * y queda en la DLQ con la cabecera x-death, disponible para análisis o reproceso.
 */
public class ProcesadorMensajes {

    public static final String CABECERA_REINTENTOS = "x-reintentos";

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);

    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties.Reintentos reintentos;

    public ProcesadorMensajes(ObjectMapper objectMapper, RabbitTemplate rabbitTemplate,
                              MensajeriaProperties.Reintentos reintentos) {
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.reintentos = reintentos;
    }

    /**
     * Convierte el mensaje al tipo indicado, ejecuta la lógica de negocio y confirma o rechaza.
     *
     * @param mensaje mensaje AMQP recibido por el @RabbitListener
     * @param canal   canal de RabbitMQ (necesario para basicAck / basicNack)
     * @param tipo    clase del payload
     * @param logica  lógica de negocio a ejecutar con el payload
     */
    public <T> void procesar(Message mensaje, Channel canal, Class<T> tipo, Consumer<T> logica) throws IOException {
        MessageProperties props = mensaje.getMessageProperties();
        long deliveryTag = props.getDeliveryTag();
        String cola = props.getConsumerQueue();
        int intento = intentosPrevios(props) + 1;

        T payload;
        try {
            payload = objectMapper.readValue(mensaje.getBody(), tipo);
        } catch (IOException e) {
            rechazarHaciaDlq(canal, deliveryTag, cola, props, "formato de mensaje inválido: " + e.getMessage());
            return;
        }

        try {
            logica.accept(payload);
            canal.basicAck(deliveryTag, false);
            log.info("[ACK] cola={} messageId={} intento={}", cola, props.getMessageId(), intento);
        } catch (ErrorRecuperableException e) {
            if (intento <= reintentos.maximo()) {
                reintentar(mensaje, canal, deliveryTag, cola, intento, e);
            } else {
                rechazarHaciaDlq(canal, deliveryTag, cola, props,
                        "reintentos agotados (" + reintentos.maximo() + "): " + e.getMessage());
            }
        } catch (RuntimeException e) {
            rechazarHaciaDlq(canal, deliveryTag, cola, props, "error no recuperable: " + e.getMessage());
        }
    }

    private void reintentar(Message mensaje, Channel canal, long deliveryTag, String cola,
                            int intentoFallido, ErrorRecuperableException causa) throws IOException {
        log.warn("[RETRY] cola={} messageId={} intento {}/{} falló: {}. Se reintentará.",
                cola, mensaje.getMessageProperties().getMessageId(), intentoFallido, reintentos.maximo() + 1,
                causa.getMessage());
        try {
            Thread.sleep(reintentos.esperaMs() * intentoFallido); // backoff lineal
            Message copia = MessageBuilder.fromMessage(mensaje)
                    .setHeader(CABECERA_REINTENTOS, intentoFallido)
                    .build();
            // Exchange por defecto ("") + nombre de la cola: vuelve exactamente a la misma cola.
            rabbitTemplate.send("", cola, copia);
            canal.basicAck(deliveryTag, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // El servicio se está deteniendo: devolvemos el mensaje a la cola para no perderlo.
            canal.basicNack(deliveryTag, false, true);
        } catch (AmqpException e) {
            rechazarHaciaDlq(canal, deliveryTag, cola, mensaje.getMessageProperties(),
                    "no fue posible re-publicar para reintento: " + e.getMessage());
        }
    }

    private void rechazarHaciaDlq(Channel canal, long deliveryTag, String cola, MessageProperties props,
                                  String motivo) throws IOException {
        log.error("[NACK -> DLQ] cola={} messageId={} intentosPrevios={} motivo={}",
                cola, props.getMessageId(), intentosPrevios(props), motivo);
        canal.basicNack(deliveryTag, false, false);
    }

    private static int intentosPrevios(MessageProperties props) {
        Object valor = props.getHeader(CABECERA_REINTENTOS);
        return valor instanceof Number n ? n.intValue() : 0;
    }
}
