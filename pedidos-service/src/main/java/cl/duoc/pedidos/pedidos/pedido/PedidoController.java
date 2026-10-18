package cl.duoc.pedidos.pedidos.pedido;

import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.pedidos.pedido.dto.CambiarEstadoRequest;
import cl.duoc.pedidos.pedidos.pedido.dto.CrearPedidoRequest;
import cl.duoc.pedidos.pedidos.pedido.dto.PedidoResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService servicio;

    public PedidoController(PedidoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<PedidoResponse> listar() {
        return servicio.listar().stream().map(PedidoResponse::desde).toList();
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return PedidoResponse.desde(servicio.obtener(id));
    }

    /** Crea el pedido y publica PEDIDO_CREADO en el exchange FANOUT. */
    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest solicitud) {
        Pedido pedido = servicio.crear(solicitud);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(pedido.getId()))
                .body(PedidoResponse.desde(pedido));
    }

    /** Cambia el estado y publica ESTADO_CAMBIADO en el exchange TOPIC (pedido.estado.&lt;estado&gt;). */
    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest solicitud) {
        return PedidoResponse.desde(servicio.cambiarEstado(id, solicitud));
    }

    /** Solicita la boleta: publica BOLETA_SOLICITADA en el exchange DIRECT. Responde 202 (proceso asíncrono). */
    @PostMapping("/{id}/boleta")
    public ResponseEntity<Map<String, Object>> solicitarBoleta(@PathVariable Long id) {
        PedidoEvento evento = servicio.solicitarBoleta(id);
        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "Boleta solicitada. Se generará de forma asíncrona en documentos-service.",
                "eventoId", evento.eventoId()));
    }
}
