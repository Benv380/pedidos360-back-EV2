package cl.duoc.pedidos.pedidos.pedido.dto;

import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import cl.duoc.pedidos.pedidos.pedido.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(
        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoPedido estado,

        /* Opcional, solo para la demostración de reintentos y DLQ. */
        SimulacionFallo simularFallo) {
}
