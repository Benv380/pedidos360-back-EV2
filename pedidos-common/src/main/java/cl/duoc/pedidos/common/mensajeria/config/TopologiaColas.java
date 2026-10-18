package cl.duoc.pedidos.common.mensajeria.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Construye colas de trabajo y DLQ con la MISMA política de retención en todos los microservicios
 * (valores de mensajeria.retencion). Cada RabbitMQConfig la usa para declarar sus colas.
 *
 * <pre>
 * Cola de trabajo                         DLQ
 *   x-dead-letter-exchange   pedidos.dlx    x-message-ttl  7 días   (al expirar se elimina)
 *   x-dead-letter-routing-key &lt;cola&gt;.dlq   x-max-length   5.000
 *   x-message-ttl            24 h           x-overflow     drop-head
 *   x-max-length             10.000
 *   x-overflow               reject-publish-dlx
 * </pre>
 */
public class TopologiaColas {

    private final MensajeriaProperties props;

    public TopologiaColas(MensajeriaProperties props) {
        this.props = props;
    }

    /** Cola durable con DLX hacia su DLQ, TTL y largo máximo. */
    public Queue colaTrabajo(MensajeriaProperties.Cola cola) {
        MensajeriaProperties.Limites limites = props.retencion().colas();
        return QueueBuilder.durable(cola.nombre())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(cola.dlq())
                .ttl((int) limites.ttlMs())
                .maxLength(limites.maxMensajes())
                .withArgument("x-overflow", desborde(limites.desborde()))
                .build();
    }

    /** DLQ durable con retención limitada: los mensajes fallidos se conservan un tiempo y luego se eliminan. */
    public Queue dlq(MensajeriaProperties.Cola cola) {
        MensajeriaProperties.Limites limites = props.retencion().dlq();
        return QueueBuilder.durable(cola.dlq())
                .ttl((int) limites.ttlMs())
                .maxLength(limites.maxMensajes())
                .withArgument("x-overflow", desborde(limites.desborde()))
                .build();
    }

    /** Enlace pedidos.dlx -> DLQ usando el nombre de la DLQ como routing key. */
    public Binding bindingDlq(Queue dlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(dlq).to(deadLetterExchange).with(dlq.getName());
    }

    /** Valida y devuelve el valor del argumento x-overflow. */
    public static String desborde(String valor) {
        return switch (valor) {
            case "drop-head", "reject-publish", "reject-publish-dlx" -> valor;
            default -> throw new IllegalArgumentException("Desborde no soportado: " + valor);
        };
    }
}
