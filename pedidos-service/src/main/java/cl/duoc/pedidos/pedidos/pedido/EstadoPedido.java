package cl.duoc.pedidos.pedidos.pedido;

import java.util.EnumSet;
import java.util.Set;

/** Ciclo de vida de un pedido y transiciones permitidas. */
public enum EstadoPedido {
    CREADO,
    PAGADO,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    public Set<EstadoPedido> siguientesPermitidos() {
        return switch (this) {
            case CREADO -> EnumSet.of(PAGADO, CANCELADO);
            case PAGADO -> EnumSet.of(DESPACHADO, CANCELADO);
            case DESPACHADO -> EnumSet.of(ENTREGADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoPedido.class);
        };
    }

    public boolean puedeCambiarA(EstadoPedido nuevo) {
        return siguientesPermitidos().contains(nuevo);
    }
}
