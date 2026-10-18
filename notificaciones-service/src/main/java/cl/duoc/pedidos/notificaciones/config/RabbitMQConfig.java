package cl.duoc.pedidos.notificaciones.config;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos.common.mensajeria.config.TopologiaColas;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ que consume este microservicio. Los exchanges vienen de pedidos-common,
 * los nombres de mensajeria.yml (MensajeriaProperties) y la retención (TTL, largo máximo, desborde)
 * de mensajeria.retencion vía {@link TopologiaColas}.
 *
 * <pre>
 * Caso de uso 1 - Notificar al cliente
 *   pedidos.eventos.fanout   ──────────────────────────► notificaciones.queue   (pedido creado)
 *   pedidos.estados.topic    ── "pedido.estado.*" ─────► notificaciones.queue   (cambio de estado)
 *   notificaciones.queue     ── NACK / TTL / desborde ─► pedidos.dlx ─► notificaciones.dlq
 *
 * Caso de uso 2 - Auditoría de todos los eventos de pedidos
 *   pedidos.eventos.fanout   ──────────────────────────► auditoria.queue
 *   pedidos.estados.topic    ── "pedido.#" ────────────► auditoria.queue
 *   pedidos.documentos.direct── "documento.boleta" ────► auditoria.queue
 *   auditoria.queue          ── NACK / TTL / desborde ─► pedidos.dlx ─► auditoria.dlq
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

    // ===================== Caso de uso 1: notificaciones =====================

    @Bean
    Queue notificacionesQueue() {
        return topologia.colaTrabajo(props.colas().notificaciones());
    }

    @Bean
    Queue notificacionesDlq() {
        return topologia.dlq(props.colas().notificaciones());
    }

    @Bean
    Binding notificacionesDesdeFanout(Queue notificacionesQueue, FanoutExchange eventosFanoutExchange) {
        return BindingBuilder.bind(notificacionesQueue).to(eventosFanoutExchange);
    }

    @Bean
    Binding notificacionesDesdeTopic(Queue notificacionesQueue, TopicExchange estadosTopicExchange) {
        return BindingBuilder.bind(notificacionesQueue).to(estadosTopicExchange)
                .with(props.routingKeys().estadosNotificables());
    }

    @Bean
    Binding notificacionesDlqBinding(Queue notificacionesDlq, DirectExchange deadLetterExchange) {
        return topologia.bindingDlq(notificacionesDlq, deadLetterExchange);
    }

    // ===================== Caso de uso 2: auditoría =====================

    @Bean
    Queue auditoriaQueue() {
        return topologia.colaTrabajo(props.colas().auditoria());
    }

    @Bean
    Queue auditoriaDlq() {
        return topologia.dlq(props.colas().auditoria());
    }

    @Bean
    Binding auditoriaDesdeFanout(Queue auditoriaQueue, FanoutExchange eventosFanoutExchange) {
        return BindingBuilder.bind(auditoriaQueue).to(eventosFanoutExchange);
    }

    @Bean
    Binding auditoriaDesdeTopic(Queue auditoriaQueue, TopicExchange estadosTopicExchange) {
        return BindingBuilder.bind(auditoriaQueue).to(estadosTopicExchange)
                .with(props.routingKeys().todosLosPedidos());
    }

    @Bean
    Binding auditoriaDesdeDirect(Queue auditoriaQueue, DirectExchange documentosDirectExchange) {
        return BindingBuilder.bind(auditoriaQueue).to(documentosDirectExchange)
                .with(props.routingKeys().documentoBoleta());
    }

    @Bean
    Binding auditoriaDlqBinding(Queue auditoriaDlq, DirectExchange deadLetterExchange) {
        return topologia.bindingDlq(auditoriaDlq, deadLetterExchange);
    }
}
