package cl.duoc.pedidos.pedidos.pedido.dto;

import cl.duoc.pedidos.pedidos.pedido.EstadoPedido;
import cl.duoc.pedidos.pedidos.pedido.Pedido;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record PedidoResponse(
        Long id,
        String cliente,
        String emailCliente,
        String descripcion,
        BigDecimal total,
        EstadoPedido estado,
        Set<EstadoPedido> siguientesEstados,
        String creadoPor,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public static PedidoResponse desde(Pedido p) {
        return new PedidoResponse(p.getId(), p.getCliente(), p.getEmailCliente(), p.getDescripcion(),
                p.getTotal(), p.getEstado(), p.getEstado().siguientesPermitidos(), p.getCreadoPor(),
                p.getFechaCreacion(), p.getFechaActualizacion());
    }
}
