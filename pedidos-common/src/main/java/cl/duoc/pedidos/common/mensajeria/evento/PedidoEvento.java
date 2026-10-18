package cl.duoc.pedidos.common.mensajeria.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contrato único de los mensajes que viajan por RabbitMQ.
 * Lo produce pedidos-service y lo consumen notificaciones-service y documentos-service.
 */
public record PedidoEvento(
        UUID eventoId,
        TipoEvento tipo,
        Long pedidoId,
        String cliente,
        String emailCliente,
        String descripcion,
        BigDecimal total,
        String estado,
        String estadoAnterior,
        String usuario,
        SimulacionFallo simularFallo,
        Instant fecha) {

    public boolean simula(SimulacionFallo fallo) {
        return simularFallo == fallo;
    }
}
