package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.rabbitadmin.admin.dto.BindingInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.BindingRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ColaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearColaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ExchangeInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.NodoInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.NombresRabbit;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PublicarPruebaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.SaludMensajeria;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * API REST del microservicio administrador de RabbitMQ.
 *
 * <pre>
 * GET    /api/admin/rabbit/salud                         totales, tasas y alertas con impacto
 * GET    /api/admin/rabbit/nodes                         nodos del clúster
 * POST   /api/admin/rabbit/queues/{nombre}/messages      publicar mensajes de prueba          201
 * POST   /api/admin/rabbit/queues/{dlq}/reprocesar?max=  devolver mensajes de una DLQ a su cola
 * GET    /api/admin/rabbit/policies                      políticas de retención
 * PUT    /api/admin/rabbit/policies/{nombre}             crear/reemplazar política (en caliente)
 * DELETE /api/admin/rabbit/policies/{nombre}             eliminar política                    204
 * GET    /api/admin/rabbit/queues                        listar colas
 * GET    /api/admin/rabbit/queues/{nombre}               detalle de una cola
 * POST   /api/admin/rabbit/queues                        crear cola (opcional: con DLQ)       201
 * DELETE /api/admin/rabbit/queues/{nombre}               eliminar cola                        204
 * DELETE /api/admin/rabbit/queues/{nombre}/messages      purgar mensajes                      200
 * GET    /api/admin/rabbit/exchanges                     listar exchanges
 * POST   /api/admin/rabbit/exchanges                     crear exchange                       201
 * DELETE /api/admin/rabbit/exchanges/{nombre}            eliminar exchange                    204
 * GET    /api/admin/rabbit/bindings                      listar bindings
 * POST   /api/admin/rabbit/bindings                      crear binding                        201
 * DELETE /api/admin/rabbit/bindings?exchange=&amp;cola=&amp;routingKey=   eliminar binding           204
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/rabbit")
public class RabbitAdminController {

    private final RabbitAdminService servicio;

    public RabbitAdminController(RabbitAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/nodes")
    public List<NodoInfo> nodos() {
        return servicio.listarNodos();
    }

    /** Totales, tasas y alertas con su impacto en rendimiento y estabilidad. */
    @GetMapping("/salud")
    public SaludMensajeria salud() {
        return servicio.salud();
    }

    // ------------------------------- Colas -------------------------------

    @GetMapping("/queues")
    public List<ColaInfo> listarColas() {
        return servicio.listarColas();
    }

    @GetMapping("/queues/{nombre}")
    public ColaInfo obtenerCola(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
            message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE) String nombre) {
        return servicio.obtenerCola(nombre);
    }

    @PostMapping("/queues")
    public ResponseEntity<ColaInfo> crearCola(@Valid @RequestBody CrearColaRequest solicitud) {
        ColaInfo cola = servicio.crearCola(solicitud);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{nombre}").build(cola.nombre())).body(cola);
    }

    @DeleteMapping("/queues/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
                                     message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE) String nombre,
                             @RequestParam(defaultValue = "false") boolean soloSiVacia) {
        servicio.eliminarCola(nombre, soloSiVacia);
    }

    @DeleteMapping("/queues/{nombre}/messages")
    public Map<String, Object> purgarCola(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
            message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE) String nombre) {
        return Map.of("cola", nombre, "mensajesEliminados", servicio.purgarCola(nombre));
    }

    /** Publica mensajes de prueba (solo colas fuera de la topología base) para demostrar límites y TTL. */
    @PostMapping("/queues/{nombre}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> publicarPrueba(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
                                                      message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE) String nombre,
                                              @Valid @RequestBody PublicarPruebaRequest solicitud) {
        return Map.of("cola", nombre, "publicados", servicio.publicarPrueba(nombre, solicitud));
    }

    /** Devuelve hasta {@code max} mensajes de una DLQ a su cola de origen. */
    @PostMapping("/queues/{nombre}/reprocesar")
    public Map<String, Object> reprocesarDlq(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
                                                     message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE) String nombre,
                                             @RequestParam(defaultValue = "100") int max) {
        return servicio.reprocesarDlq(nombre, max);
    }

    // ------------------------------ Políticas ------------------------------

    @GetMapping("/policies")
    public List<PoliticaInfo> listarPoliticas() {
        return servicio.listarPoliticas();
    }

    /** Crea o reemplaza (idempotente) una política de retención aplicada en caliente. */
    @PutMapping("/policies/{nombre}")
    public PoliticaInfo guardarPolitica(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
                                                message = "El nombre de la política " + NombresRabbit.MENSAJE_NOMBRE) String nombre,
                                        @Valid @RequestBody PoliticaRequest solicitud) {
        return servicio.guardarPolitica(nombre, solicitud);
    }

    @DeleteMapping("/policies/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPolitica(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
            message = "El nombre de la política " + NombresRabbit.MENSAJE_NOMBRE) String nombre) {
        servicio.eliminarPolitica(nombre);
    }

    // ----------------------------- Exchanges -----------------------------

    @GetMapping("/exchanges")
    public List<ExchangeInfo> listarExchanges() {
        return servicio.listarExchanges();
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeInfo crearExchange(@Valid @RequestBody CrearExchangeRequest solicitud) {
        return servicio.crearExchange(solicitud);
    }

    @DeleteMapping("/exchanges/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarExchange(@PathVariable @Pattern(regexp = NombresRabbit.PATRON_NOMBRE,
            message = "El nombre del exchange " + NombresRabbit.MENSAJE_NOMBRE) String nombre) {
        servicio.eliminarExchange(nombre);
    }

    // ------------------------------ Bindings ------------------------------

    @GetMapping("/bindings")
    public List<BindingInfo> listarBindings() {
        return servicio.listarBindings();
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingInfo crearBinding(@Valid @RequestBody BindingRequest solicitud) {
        return servicio.crearBinding(solicitud);
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBinding(@RequestParam @NotBlank(message = "El exchange es obligatorio") String exchange,
                                @RequestParam @NotBlank(message = "La cola es obligatoria") String cola,
                                @RequestParam(defaultValue = "") String routingKey) {
        servicio.eliminarBinding(new BindingRequest(exchange, cola, routingKey));
    }
}
