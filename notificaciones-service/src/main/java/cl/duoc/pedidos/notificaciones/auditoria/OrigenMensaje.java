package cl.duoc.pedidos.notificaciones.auditoria;

/** Por dónde llegó el evento (exchange y routing key), sin exponer tipos de RabbitMQ al servicio. */
public record OrigenMensaje(String exchange, String routingKey) {
}
