package cl.duoc.pedidos.notificaciones.notificacion;

import cl.duoc.pedidos.common.mensajeria.consumo.ErrorNoRecuperableException;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lógica de negocio de notificaciones. No conoce RabbitMQ. */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository repositorio;

    public NotificacionService(NotificacionRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void notificar(PedidoEvento evento) {
        if (repositorio.existsByEventoId(evento.eventoId())) {
            log.info("Notificación del evento {} ya enviada; se ignora el duplicado", evento.eventoId());
            return;
        }
        if (evento.emailCliente() == null || !evento.emailCliente().contains("@")) {
            throw new ErrorNoRecuperableException("el pedido " + evento.pedidoId() + " no tiene un email válido");
        }
        if (evento.simula(SimulacionFallo.NOTIFICACION)) {
            throw new ErrorNoRecuperableException("SIMULADO: el proveedor de correo rechazó el destinatario "
                    + evento.emailCliente());
        }

        String asunto;
        String mensaje;
        switch (evento.tipo()) {
            case PEDIDO_CREADO -> {
                asunto = "Recibimos tu pedido #" + evento.pedidoId();
                mensaje = "Hola " + evento.cliente() + ", registramos tu pedido \"" + evento.descripcion()
                        + "\" por $" + evento.total() + ".";
            }
            case ESTADO_CAMBIADO -> {
                asunto = "Tu pedido #" + evento.pedidoId() + " ahora está " + evento.estado();
                mensaje = "Hola " + evento.cliente() + ", tu pedido pasó de " + evento.estadoAnterior()
                        + " a " + evento.estado() + ".";
            }
            default -> throw new ErrorNoRecuperableException("tipo de evento no notificable: " + evento.tipo());
        }

        // Envío simulado del correo (en producción: Azure Communication Services, SendGrid, etc.)
        log.info("[EMAIL] para={} asunto=\"{}\"", evento.emailCliente(), asunto);
        repositorio.save(new Notificacion(evento.eventoId(), evento.pedidoId(), evento.emailCliente(), asunto, mensaje));
    }

    @Transactional(readOnly = true)
    public List<Notificacion> ultimas() {
        return repositorio.findTop100ByOrderByIdDesc();
    }
}
