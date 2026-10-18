package cl.duoc.pedidos.pedidos.pedido;

import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import cl.duoc.pedidos.common.mensajeria.evento.TipoEvento;
import cl.duoc.pedidos.common.seguridad.UsuarioActual;
import cl.duoc.pedidos.common.web.RecursoNoEncontradoException;
import cl.duoc.pedidos.common.web.ReglaNegocioException;
import cl.duoc.pedidos.pedidos.pedido.dto.CambiarEstadoRequest;
import cl.duoc.pedidos.pedidos.pedido.dto.CrearPedidoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de negocio de pedidos. No conoce RabbitMQ: solo usa la interfaz {@link EventosPedidoPublisher}.
 * Las operaciones son transaccionales: si la publicación del evento falla, el cambio en BD se revierte.
 */
@Service
public class PedidoService {

    private final PedidoRepository repositorio;
    private final EventosPedidoPublisher eventos;

    public PedidoService(PedidoRepository repositorio, EventosPedidoPublisher eventos) {
        this.repositorio = repositorio;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listar() {
        return repositorio.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Pedido obtener(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + id));
    }

    @Transactional
    public Pedido crear(CrearPedidoRequest solicitud) {
        Pedido pedido = repositorio.save(new Pedido(solicitud.cliente().trim(), solicitud.emailCliente().trim(),
                solicitud.descripcion().trim(), solicitud.total(), UsuarioActual.nombre()));
        eventos.publicarPedidoCreado(evento(TipoEvento.PEDIDO_CREADO, pedido, null, solicitud.simularFallo()));
        return pedido;
    }

    @Transactional
    public Pedido cambiarEstado(Long id, CambiarEstadoRequest solicitud) {
        Pedido pedido = obtener(id);
        EstadoPedido anterior = pedido.getEstado();
        EstadoPedido nuevo = solicitud.estado();
        if (!anterior.puedeCambiarA(nuevo)) {
            throw new ReglaNegocioException("No se puede pasar de " + anterior + " a " + nuevo
                    + ". Permitidos: " + anterior.siguientesPermitidos());
        }
        pedido.cambiarEstado(nuevo);
        repositorio.save(pedido);
        eventos.publicarCambioEstado(evento(TipoEvento.ESTADO_CAMBIADO, pedido, anterior, solicitud.simularFallo()));
        return pedido;
    }

    @Transactional(readOnly = true)
    public PedidoEvento solicitarBoleta(Long id) {
        Pedido pedido = obtener(id);
        if (pedido.getEstado() == EstadoPedido.CREADO || pedido.getEstado() == EstadoPedido.CANCELADO) {
            throw new ReglaNegocioException("Solo se emite boleta de pedidos pagados (estado actual: "
                    + pedido.getEstado() + ")");
        }
        PedidoEvento evento = evento(TipoEvento.BOLETA_SOLICITADA, pedido, null, SimulacionFallo.NINGUNA);
        eventos.solicitarBoleta(evento);
        return evento;
    }

    private static PedidoEvento evento(TipoEvento tipo, Pedido p, EstadoPedido anterior, SimulacionFallo fallo) {
        return new PedidoEvento(UUID.randomUUID(), tipo, p.getId(), p.getCliente(), p.getEmailCliente(),
                p.getDescripcion(), p.getTotal(), p.getEstado().name(), anterior != null ? anterior.name() : null,
                UsuarioActual.nombre(), fallo != null ? fallo : SimulacionFallo.NINGUNA, Instant.now());
    }
}
