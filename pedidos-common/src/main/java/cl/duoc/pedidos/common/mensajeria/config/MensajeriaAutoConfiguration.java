package cl.duoc.pedidos.common.mensajeria.config;

import cl.duoc.pedidos.common.mensajeria.consumo.ProcesadorMensajes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Infraestructura de mensajería compartida por todos los microservicios.
 * <ul>
 *   <li>Declara los exchanges (fanout, topic, direct y el dead-letter exchange).
 *       Las colas, DLQ y bindings los declara cada microservicio en su propia clase RabbitMQConfig.</li>
 *   <li>Serialización JSON de los mensajes.</li>
 *   <li>Publisher confirms / returns: se registra si el broker aceptó o no cada mensaje publicado.</li>
 *   <li>{@link ProcesadorMensajes}: ACK / reintento / NACK hacia DLQ para los consumidores.</li>
 * </ul>
 * Ninguna clase de negocio contiene configuración de RabbitMQ.
 */
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@EnableConfigurationProperties(MensajeriaProperties.class)
public class MensajeriaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MensajeriaAutoConfiguration.class);

    // ---------- Exchanges (compartidos por productor y consumidores) ----------

    /** FANOUT: difunde PEDIDO_CREADO a todas las colas enlazadas, ignorando la routing key. */
    @Bean
    FanoutExchange eventosFanoutExchange(MensajeriaProperties props) {
        return ExchangeBuilder.fanoutExchange(props.exchanges().eventosFanout()).durable(true).build();
    }

    /** TOPIC: enruta cambios de estado por patrón (pedido.estado.*, pedido.#). */
    @Bean
    TopicExchange estadosTopicExchange(MensajeriaProperties props) {
        return ExchangeBuilder.topicExchange(props.exchanges().estadosTopic()).durable(true).build();
    }

    /** DIRECT: enruta por routing key exacta (documento.boleta). */
    @Bean
    DirectExchange documentosDirectExchange(MensajeriaProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().documentosDirect()).durable(true).build();
    }

    /** Dead Letter Exchange: recibe los mensajes rechazados (NACK) y los enruta a la DLQ de cada cola. */
    @Bean
    DirectExchange deadLetterExchange(MensajeriaProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ---------- Serialización y confirmaciones ----------

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplateCustomizer confirmacionesPublicacion() {
        return template -> {
            template.setConfirmCallback((correlacion, ack, causa) -> {
                String id = correlacion != null ? correlacion.getId() : "?";
                if (ack) {
                    log.info("[CONFIRM] el broker aceptó el mensaje eventoId={}", id);
                } else {
                    log.error("[CONFIRM] el broker RECHAZÓ el mensaje eventoId={} causa={}", id, causa);
                }
            });
            template.setReturnsCallback(devuelto -> log.error(
                    "[RETURN] mensaje sin cola de destino exchange={} routingKey={} replyText={}",
                    devuelto.getExchange(), devuelto.getRoutingKey(), devuelto.getReplyText()));
        };
    }

    /**
     * Fábrica de los @RabbitListener: misma configuración de Spring Boot (ACK manual, prefetch...),
     * pero entrega el mensaje crudo. La deserialización la hace {@link ProcesadorMensajes}, así un JSON
     * inválido termina en NACK hacia la DLQ en vez de quedar sin confirmar.
     */
    @Bean(name = "rabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        return factory;
    }

    @Bean
    TopologiaColas topologiaColas(MensajeriaProperties props) {
        return new TopologiaColas(props);
    }

    @Bean
    ProcesadorMensajes procesadorMensajes(ObjectMapper objectMapper, RabbitTemplate rabbitTemplate,
                                          MensajeriaProperties props) {
        return new ProcesadorMensajes(objectMapper, rabbitTemplate, props.reintentos());
    }
}
