package cl.duoc.pedidos.common.mensajeria.control;

import cl.duoc.pedidos.common.web.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Control operativo de los consumidores (@RabbitListener) de este microservicio.
 * <p>
 * Pausar un consumidor detiene la entrega de mensajes: los nuevos quedan PENDIENTES en la cola
 * (Ready). Al reanudar, se procesan los acumulados. Sirve para mantenimiento (ej. un proveedor externo
 * caído) y para observar en vivo el efecto de no tener consumidores: backlog, TTL y DLQ.
 * Se habilita definiendo mensajeria.control.ruta (ej: /api/documentos/consumidores).
 */
@RestController
@RequestMapping("${mensajeria.control.ruta}")
public class ControlConsumidoresController {

    private static final Logger log = LoggerFactory.getLogger(ControlConsumidoresController.class);

    private final RabbitListenerEndpointRegistry registro;

    public ControlConsumidoresController(RabbitListenerEndpointRegistry registro) {
        this.registro = registro;
    }

    @GetMapping
    public List<EstadoConsumidor> listar() {
        return registro.getListenerContainerIds().stream()
                .sorted(Comparator.naturalOrder())
                .map(id -> estado(id, registro.getListenerContainer(id)))
                .toList();
    }

    @PostMapping("/{id}/pausar")
    public EstadoConsumidor pausar(@PathVariable String id) {
        MessageListenerContainer contenedor = contenedor(id);
        contenedor.stop();
        log.warn("[CONSUMIDOR] '{}' PAUSADO: los mensajes nuevos quedarán pendientes en la cola", id);
        return estado(id, contenedor);
    }

    @PostMapping("/{id}/reanudar")
    public EstadoConsumidor reanudar(@PathVariable String id) {
        MessageListenerContainer contenedor = contenedor(id);
        contenedor.start();
        log.info("[CONSUMIDOR] '{}' REANUDADO: se procesan los mensajes pendientes", id);
        return estado(id, contenedor);
    }

    private MessageListenerContainer contenedor(String id) {
        MessageListenerContainer contenedor = registro.getListenerContainer(id);
        if (contenedor == null) {
            throw new RecursoNoEncontradoException("No existe el consumidor '" + id + "'. Disponibles: "
                    + registro.getListenerContainerIds());
        }
        return contenedor;
    }

    private static EstadoConsumidor estado(String id, MessageListenerContainer contenedor) {
        List<String> colas = contenedor instanceof AbstractMessageListenerContainer amlc
                ? Arrays.asList(amlc.getQueueNames())
                : List.of();
        int activos = contenedor instanceof SimpleMessageListenerContainer smlc && smlc.isRunning()
                ? smlc.getActiveConsumerCount()
                : 0;
        return new EstadoConsumidor(id, colas, contenedor.isRunning(), activos);
    }

    /**
     * @param consumidoresActivos hilos consumidores abiertos ahora (escala entre concurrency y max-concurrency)
     */
    public record EstadoConsumidor(String id, List<String> colas, boolean activo, int consumidoresActivos) {
    }
}
