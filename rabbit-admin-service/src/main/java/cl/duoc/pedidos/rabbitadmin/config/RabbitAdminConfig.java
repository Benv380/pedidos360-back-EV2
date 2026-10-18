package cl.duoc.pedidos.rabbitadmin.config;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Configuración del microservicio administrador:
 * <ul>
 *   <li>Cliente HTTP hacia la API de RabbitMQ Management (mismas credenciales que spring.rabbitmq).</li>
 *   <li>Conjunto de recursos protegidos: la topología base del sistema (definida en mensajeria.yml)
 *       no se puede eliminar desde la API.</li>
 * </ul>
 * La operación sobre colas/exchanges/bindings usa el bean AmqpAdmin (RabbitAdmin) que crea Spring Boot.
 */
@Configuration
public class RabbitAdminConfig {

    @Bean
    RestClient rabbitManagementRestClient(RabbitAdminProperties props, RabbitProperties rabbit) {
        return RestClient.builder()
                .baseUrl(props.managementUrl())
                .defaultHeaders(h -> h.setBasicAuth(rabbit.getUsername(), rabbit.getPassword()))
                .build();
    }

    @Bean
    RecursosProtegidos recursosProtegidos(MensajeriaProperties m) {
        MensajeriaProperties.Colas c = m.colas();
        MensajeriaProperties.Exchanges e = m.exchanges();
        return new RecursosProtegidos(
                Set.of(c.notificaciones().nombre(), c.notificaciones().dlq(),
                        c.documentos().nombre(), c.documentos().dlq(),
                        c.auditoria().nombre(), c.auditoria().dlq()),
                Set.of(e.eventosFanout(), e.estadosTopic(), e.documentosDirect(), e.deadLetter()));
    }

    /** Colas y exchanges de la topología base que la API no permite eliminar. */
    public record RecursosProtegidos(Set<String> colas, Set<String> exchanges) {
    }
}
