package cl.duoc.pedidos.notificaciones.auditoria;

import cl.duoc.pedidos.common.mensajeria.consumo.ProcesadorMensajes;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "auditoría" (auditoria.queue).
 * Recibe eventos desde los tres exchanges: fanout, topic ("pedido.#") y direct ("documento.boleta").
 */
@Component
public class AuditoriaListener {

    private final ProcesadorMensajes procesador;
    private final AuditoriaService servicio;

    public AuditoriaListener(ProcesadorMensajes procesador, AuditoriaService servicio) {
        this.procesador = procesador;
        this.servicio = servicio;
    }

    @RabbitListener(id = "auditoria", queues = "${mensajeria.colas.auditoria.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        MessageProperties props = mensaje.getMessageProperties();
        OrigenMensaje origen = new OrigenMensaje(props.getReceivedExchange(), props.getReceivedRoutingKey());
        procesador.procesar(mensaje, canal, PedidoEvento.class, evento -> servicio.registrar(evento, origen));
    }
}
