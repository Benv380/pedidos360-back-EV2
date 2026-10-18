package cl.duoc.pedidos.notificaciones.auditoria;

import cl.duoc.pedidos.common.mensajeria.consumo.ErrorNoRecuperableException;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lógica de negocio de auditoría. No conoce RabbitMQ. */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final EventoAuditoriaRepository repositorio;

    public AuditoriaService(EventoAuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void registrar(PedidoEvento evento, OrigenMensaje origen) {
        if (evento.eventoId() == null || evento.tipo() == null || evento.pedidoId() == null) {
            throw new ErrorNoRecuperableException("evento incompleto: faltan eventoId, tipo o pedidoId");
        }
        if (evento.simula(SimulacionFallo.AUDITORIA)) {
            throw new ErrorNoRecuperableException("SIMULADO: el evento no cumple el esquema de auditoría");
        }
        if (repositorio.existsByEventoId(evento.eventoId())) {
            log.info("Evento {} ya auditado; se ignora el duplicado", evento.eventoId());
            return;
        }
        repositorio.save(new EventoAuditoria(evento.eventoId(), evento.tipo().name(), evento.pedidoId(),
                evento.estado(), evento.estadoAnterior(), evento.usuario(), origen.exchange(),
                origen.routingKey(), evento.fecha()));
        log.info("[AUDITORIA] {} pedido={} via exchange={} routingKey='{}'",
                evento.tipo(), evento.pedidoId(), origen.exchange(), origen.routingKey());
    }

    @Transactional(readOnly = true)
    public List<EventoAuditoria> ultimos() {
        return repositorio.findTop200ByOrderByIdDesc();
    }
}
