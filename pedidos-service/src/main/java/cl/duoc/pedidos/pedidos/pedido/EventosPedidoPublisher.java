package cl.duoc.pedidos.pedidos.pedido;

import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;

/**
 * Puerto de salida del dominio de pedidos. El servicio de negocio solo conoce esta interfaz;
 * la implementación con RabbitMQ vive en el paquete "mensajeria".
 */
public interface EventosPedidoPublisher {

    /** Difunde la creación del pedido a todos los interesados (exchange FANOUT). */
    void publicarPedidoCreado(PedidoEvento evento);

    /** Publica un cambio de estado enrutado por patrón (exchange TOPIC). */
    void publicarCambioEstado(PedidoEvento evento);

    /** Solicita la emisión de la boleta (exchange DIRECT). */
    void solicitarBoleta(PedidoEvento evento);
}
