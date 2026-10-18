package cl.duoc.pedidos.pedidos.mensajeria;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.pedidos.pedido.EventosPedidoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * PRODUCTOR: implementación de {@link EventosPedidoPublisher} sobre RabbitMQ.
 * Los nombres de exchanges y routing keys vienen de {@link MensajeriaProperties} (mensajeria.yml).
 */
@Component
public class RabbitEventosPedidoPublisher implements EventosPedidoPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitEventosPedidoPublisher.class);
    /** Un exchange fanout ignora la routing key. */
    private static final String SIN_ROUTING_KEY = "";

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties props;

    public RabbitEventosPedidoPublisher(RabbitTemplate rabbitTemplate, MensajeriaProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Override
    public void publicarPedidoCreado(PedidoEvento evento) {
        publicar(props.exchanges().eventosFanout(), SIN_ROUTING_KEY, evento);
    }

    @Override
    public void publicarCambioEstado(PedidoEvento evento) {
        publicar(props.exchanges().estadosTopic(), props.routingKeys().paraEstado(evento.estado()), evento);
    }

    @Override
    public void solicitarBoleta(PedidoEvento evento) {
        publicar(props.exchanges().documentosDirect(), props.routingKeys().documentoBoleta(), evento);
    }

    private void publicar(String exchange, String routingKey, PedidoEvento evento) {
        String id = evento.eventoId().toString();
        rabbitTemplate.convertAndSend(exchange, routingKey, evento, mensaje -> {
            mensaje.getMessageProperties().setMessageId(id);
            mensaje.getMessageProperties().setType(evento.tipo().name());
            mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return mensaje;
        }, new CorrelationData(id));
        log.info("[PUBLISH] {} pedido={} exchange={} routingKey='{}' eventoId={}",
                evento.tipo(), evento.pedidoId(), exchange, routingKey, id);
    }
}
