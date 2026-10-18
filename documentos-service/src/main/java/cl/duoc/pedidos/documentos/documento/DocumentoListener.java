package cl.duoc.pedidos.documentos.documento;

import cl.duoc.pedidos.common.mensajeria.consumo.ProcesadorMensajes;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CONSUMIDOR del dominio "documentos" (documentos.queue).
 * Recibe PEDIDO_CREADO (vía fanout) para el ticket y BOLETA_SOLICITADA (vía direct "documento.boleta").
 */
@Component
public class DocumentoListener {

    private final ProcesadorMensajes procesador;
    private final DocumentoService servicio;

    public DocumentoListener(ProcesadorMensajes procesador, DocumentoService servicio) {
        this.procesador = procesador;
        this.servicio = servicio;
    }

    @RabbitListener(id = "documentos", queues = "${mensajeria.colas.documentos.nombre}")
    public void recibir(Message mensaje, Channel canal) throws IOException {
        procesador.procesar(mensaje, canal, PedidoEvento.class, servicio::generar);
    }
}
