package cl.duoc.pedidos.notificaciones.notificacion;

import cl.duoc.pedidos.common.mensajeria.consumo.ProcesadorMensajes;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "notificaciones" (notificaciones.queue).
 * Recibe PEDIDO_CREADO (vía fanout) y ESTADO_CAMBIADO (vía topic "pedido.estado.*").
 * El ACK/NACK lo gestiona {@link ProcesadorMensajes}.
 */
@Component
public class NotificacionListener {

    private final ProcesadorMensajes procesador;
    private final NotificacionService servicio;

    public NotificacionListener(ProcesadorMensajes procesador, NotificacionService servicio) {
        this.procesador = procesador;
        this.servicio = servicio;
    }

    @RabbitListener(id = "notificaciones", queues = "${mensajeria.colas.notificaciones.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        procesador.procesar(mensaje, canal, PedidoEvento.class, servicio::notificar);
    }
}
