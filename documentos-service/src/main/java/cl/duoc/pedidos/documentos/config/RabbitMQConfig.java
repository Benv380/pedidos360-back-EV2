package cl.duoc.pedidos.documentos.config;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos.common.mensajeria.config.TopologiaColas;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ que consume este microservicio (nombres desde mensajeria.yml y retención
 * desde mensajeria.retencion vía {@link TopologiaColas}).
 *
 * <pre>
 * Caso de uso - Generación de documentos
 *   pedidos.eventos.fanout    ──────────────────────────► documentos.queue   (TICKET del pedido creado)
 *   pedidos.documentos.direct ── "documento.boleta" ────► documentos.queue   (BOLETA solicitada)
 *   documentos.queue          ── NACK / TTL / desborde ─► pedidos.dlx ─► documentos.dlq
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    private final MensajeriaProperties props;
    private final TopologiaColas topologia;

    public RabbitMQConfig(MensajeriaProperties props, TopologiaColas topologia) {
        this.props = props;
        this.topologia = topologia;
    }

    @Bean
    Queue documentosQueue() {
        return topologia.colaTrabajo(props.colas().documentos());
    }

    @Bean
    Queue documentosDlq() {
        return topologia.dlq(props.colas().documentos());
    }

    @Bean
    Binding documentosDesdeFanout(Queue documentosQueue, FanoutExchange eventosFanoutExchange) {
        return BindingBuilder.bind(documentosQueue).to(eventosFanoutExchange);
    }

    @Bean
    Binding documentosDesdeDirect(Queue documentosQueue, DirectExchange documentosDirectExchange) {
        return BindingBuilder.bind(documentosQueue).to(documentosDirectExchange)
                .with(props.routingKeys().documentoBoleta());
    }

    @Bean
    Binding documentosDlqBinding(Queue documentosDlq, DirectExchange deadLetterExchange) {
        return topologia.bindingDlq(documentosDlq, deadLetterExchange);
    }
}
