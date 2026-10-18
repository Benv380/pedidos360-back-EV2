package cl.duoc.pedidos.common.mensajeria.control;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/** Activa el control de consumidores solo en los microservicios que definen mensajeria.control.ruta. */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnProperty(prefix = "mensajeria.control", name = "ruta")
@Import(ControlConsumidoresController.class)
public class ControlConsumidoresAutoConfiguration {
}
