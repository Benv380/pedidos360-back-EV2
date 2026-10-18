package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos.common.mensajeria.config.TopologiaColas;
import cl.duoc.pedidos.common.mensajeria.consumo.ProcesadorMensajes;
import cl.duoc.pedidos.common.web.RecursoNoEncontradoException;
import cl.duoc.pedidos.common.web.ReglaNegocioException;
import cl.duoc.pedidos.common.web.SolicitudInvalidaException;
import cl.duoc.pedidos.rabbitadmin.admin.dto.BindingInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.BindingRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ColaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearColaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearExchangeRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ExchangeInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.NodoInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PublicarPruebaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.RetencionInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.SaludMensajeria;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoExchange;
import cl.duoc.pedidos.rabbitadmin.config.RabbitAdminConfig.RecursosProtegidos;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio dedicado que encapsula TODA la administración de RabbitMQ: colas, exchanges, bindings,
 * políticas de retención, métricas/salud y operaciones sobre DLQ. El controlador solo invoca estos
 * métodos de alto nivel y no conoce AmqpAdmin ni la API de Management.
 * <ul>
 *   <li>Escritura de topología: AMQP mediante {@link AmqpAdmin} (RabbitAdmin).</li>
 *   <li>Lectura de métricas y políticas: API HTTP de RabbitMQ Management ({@link RabbitManagementClient}).</li>
 * </ul>
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final String SUFIJO_DLQ = ".dlq";

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitManagementClient management;
    private final ValidadorTopologia validador;
    private final AnalizadorSalud analizador;
    private final RecursosProtegidos protegidos;
    private final MensajeriaProperties mensajeria;
    private final int prefetch;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, RabbitManagementClient management,
                              ValidadorTopologia validador, AnalizadorSalud analizador,
                              RecursosProtegidos protegidos, MensajeriaProperties mensajeria,
                              RabbitProperties rabbitProperties) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.management = management;
        this.validador = validador;
        this.analizador = analizador;
        this.protegidos = protegidos;
        this.mensajeria = mensajeria;
        Integer pf = rabbitProperties.getListener().getSimple().getPrefetch();
        this.prefetch = pf != null ? pf : 250;
    }

    // =============================== Colas ===============================

    public List<ColaInfo> listarColas() {
        return management.colas().stream()
                .map(this::aColaInfo)
                .sorted(Comparator.comparing(ColaInfo::nombre))
                .toList();
    }

    public ColaInfo obtenerCola(String nombre) {
        return management.cola(nombre).map(this::aColaInfo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cola '" + nombre + "'"));
    }

    /** Crea la cola con sus límites de retención (y opcionalmente su DLQ con la retención estándar de DLQ). */
    public ColaInfo crearCola(CrearColaRequest solicitud) {
        validador.validarCola(solicitud);
        String nombre = solicitud.nombre();
        if (amqpAdmin.getQueueInfo(nombre) != null) {
            throw new ReglaNegocioException("La cola '" + nombre + "' ya existe");
        }

        QueueBuilder builder = (solicitud.esDurable() ? QueueBuilder.durable(nombre) : QueueBuilder.nonDurable(nombre))
                .withArgument("x-queue-type", solicitud.tipoEfectivo().argumento());
        if (solicitud.esAutoDelete()) {
            builder.autoDelete();
        }
        if (solicitud.ttlMs() != null) {
            builder.ttl(solicitud.ttlMs().intValue());
        }
        if (solicitud.maxMensajes() != null) {
            builder.maxLength(solicitud.maxMensajes());
        }
        if (solicitud.desborde() != null) {
            builder.withArgument("x-overflow", solicitud.desborde().valor());
        }
        if (solicitud.expiraSinUsoMs() != null) {
            builder.expires(solicitud.expiraSinUsoMs().intValue());
        }

        if (solicitud.requiereDlq()) {
            String dlq = nombre + SUFIJO_DLQ;
            if (amqpAdmin.getQueueInfo(dlq) != null) {
                throw new ReglaNegocioException("Ya existe la cola '" + dlq + "' que se usaría como DLQ");
            }
            String dlx = mensajeria.exchanges().deadLetter();
            MensajeriaProperties.Limites limitesDlq = mensajeria.retencion().dlq();
            amqpAdmin.declareQueue(QueueBuilder.durable(dlq)
                    .ttl((int) limitesDlq.ttlMs())
                    .maxLength(limitesDlq.maxMensajes())
                    .withArgument("x-overflow", TopologiaColas.desborde(limitesDlq.desborde()))
                    .build());
            amqpAdmin.declareBinding(new Binding(dlq, Binding.DestinationType.QUEUE, dlx, dlq, null));
            builder.deadLetterExchange(dlx).deadLetterRoutingKey(dlq);
            log.info("[ADMIN] DLQ creada: {} (enlazada a {}, retención {} ms / {} msgs)", dlq, dlx,
                    limitesDlq.ttlMs(), limitesDlq.maxMensajes());
        }

        Queue cola = builder.build();
        amqpAdmin.declareQueue(cola);
        log.info("[ADMIN] cola creada: {} argumentos={}", nombre, cola.getArguments());
        return obtenerCola(nombre);
    }

    public void eliminarCola(String nombre, boolean soloSiVacia) {
        validador.validarNombre(nombre, "la cola");
        if (protegidos.colas().contains(nombre)) {
            throw new ReglaNegocioException("La cola '" + nombre + "' es parte de la topología base del sistema y no se puede eliminar");
        }
        var info = amqpAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + nombre + "'");
        }
        if (soloSiVacia && info.getMessageCount() > 0) {
            throw new ReglaNegocioException("La cola '" + nombre + "' tiene mensajes pendientes; purgue o elimine sin 'soloSiVacia'");
        }
        amqpAdmin.deleteQueue(nombre);
        log.info("[ADMIN] cola eliminada: {}", nombre);
    }

    /** Limpieza manual: elimina todos los mensajes listos de la cola (los no confirmados se conservan). */
    public int purgarCola(String nombre) {
        validador.validarNombre(nombre, "la cola");
        if (amqpAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + nombre + "'");
        }
        int eliminados = amqpAdmin.purgeQueue(nombre);
        log.warn("[ADMIN] cola purgada: {} ({} mensajes eliminados)", nombre, eliminados);
        return eliminados;
    }

    /**
     * Publica mensajes sintéticos en una cola (no en las de la topología base) para observar en vivo
     * el largo máximo, el desborde y el TTL.
     */
    public int publicarPrueba(String nombre, PublicarPruebaRequest solicitud) {
        validador.validarNombre(nombre, "la cola");
        if (protegidos.colas().contains(nombre)) {
            throw new ReglaNegocioException("No se publican mensajes de prueba en colas de la topología base; "
                    + "cree una cola de demostración (ej: demo.queue)");
        }
        if (amqpAdmin.getQueueInfo(nombre) == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + nombre + "'");
        }
        String contenido = solicitud.contenido() != null ? solicitud.contenido() : "mensaje de prueba";
        for (int i = 1; i <= solicitud.cantidad(); i++) {
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setMessageId(UUID.randomUUID().toString());
            String json = "{\"prueba\":true,\"n\":" + i + ",\"contenido\":\"" + contenido.replace("\"", "'")
                    + "\",\"fecha\":\"" + Instant.now() + "\"}";
            rabbitTemplate.send("", nombre, new Message(json.getBytes(StandardCharsets.UTF_8), props));
        }
        log.info("[ADMIN] {} mensajes de prueba publicados en {}", solicitud.cantidad(), nombre);
        return solicitud.cantidad();
    }

    /**
     * Devuelve mensajes de una DLQ a su cola de origen (tomada de la cabecera x-death) para reprocesarlos.
     * Por cada mensaje: basicGet sin auto-ack -> publish a la cola original -> basicAck. Si la publicación
     * falla, el mensaje NO se confirma y permanece en la DLQ.
     */
    public Map<String, Object> reprocesarDlq(String dlq, int maximo) {
        validador.validarNombre(dlq, "la DLQ");
        if (!dlq.endsWith(SUFIJO_DLQ)) {
            throw new SolicitudInvalidaException("Solo se reprocesan colas DLQ (terminadas en '.dlq')");
        }
        if (maximo < 1 || maximo > 1000) {
            throw new SolicitudInvalidaException("El máximo a reprocesar debe estar entre 1 y 1000");
        }
        var info = amqpAdmin.getQueueInfo(dlq);
        if (info == null) {
            throw new RecursoNoEncontradoException("No existe la DLQ '" + dlq + "'");
        }
        // Solo los mensajes presentes al inicio: si uno vuelve a fallar y regresa a la DLQ durante el
        // reproceso, no se vuelve a tomar en esta misma ejecución (evita un bucle).
        int limite = Math.min(maximo, info.getMessageCount());
        String origenPorDefecto = dlq.substring(0, dlq.length() - SUFIJO_DLQ.length());
        Map<String, Integer> porDestino = new LinkedHashMap<>();
        int total = 0;
        for (int i = 0; i < limite; i++) {
            String destino = rabbitTemplate.execute(canal -> {
                GetResponse respuesta = canal.basicGet(dlq, false);
                if (respuesta == null) {
                    return null;
                }
                AMQP.BasicProperties props = respuesta.getProps();
                Map<String, Object> cabeceras = props.getHeaders() != null ? new HashMap<>(props.getHeaders()) : new HashMap<>();
                String colaOriginal = colaOriginal(cabeceras, origenPorDefecto);
                cabeceras.keySet().removeIf(k -> k.startsWith("x-death") || k.startsWith("x-first-death")
                        || k.startsWith("x-last-death") || k.equals(ProcesadorMensajes.CABECERA_REINTENTOS));
                cabeceras.put("x-reprocesado-desde", dlq);
                canal.basicPublish("", colaOriginal, props.builder().headers(cabeceras).build(), respuesta.getBody());
                canal.basicAck(respuesta.getEnvelope().getDeliveryTag(), false);
                return colaOriginal;
            });
            if (destino == null) {
                break;
            }
            porDestino.merge(destino, 1, Integer::sum);
            total++;
        }
        log.info("[ADMIN] reproceso de {}: {} mensajes devueltos {}", dlq, total, porDestino);
        return Map.of("dlq", dlq, "reprocesados", total, "destinos", porDestino);
    }

    @SuppressWarnings("unchecked")
    private static String colaOriginal(Map<String, Object> cabeceras, String porDefecto) {
        if (cabeceras.get("x-death") instanceof List<?> muertes && !muertes.isEmpty()
                && muertes.get(0) instanceof Map<?, ?> primera && primera.get("queue") != null) {
            return primera.get("queue").toString();
        }
        return porDefecto;
    }

    // ============================= Exchanges =============================

    public List<ExchangeInfo> listarExchanges() {
        return management.exchanges().stream()
                .map(this::aExchangeInfo)
                .filter(e -> !e.nombre().isEmpty() && !e.nombre().startsWith("amq."))
                .sorted(Comparator.comparing(ExchangeInfo::nombre))
                .toList();
    }

    public ExchangeInfo crearExchange(CrearExchangeRequest solicitud) {
        validador.validarNombre(solicitud.nombre(), "el exchange");
        if (management.exchange(solicitud.nombre()).isPresent()) {
            throw new ReglaNegocioException("El exchange '" + solicitud.nombre() + "' ya existe");
        }
        ExchangeBuilder builder = new ExchangeBuilder(solicitud.nombre(), solicitud.tipo().tipoAmqp())
                .durable(solicitud.esDurable());
        if (solicitud.esAutoDelete()) {
            builder.autoDelete();
        }
        Exchange exchange = builder.build();
        amqpAdmin.declareExchange(exchange);
        log.info("[ADMIN] exchange creado: {} ({})", solicitud.nombre(), solicitud.tipo());
        return new ExchangeInfo(solicitud.nombre(), solicitud.tipo().name(), solicitud.esDurable(),
                solicitud.esAutoDelete(), false);
    }

    public void eliminarExchange(String nombre) {
        validador.validarNombre(nombre, "el exchange");
        if (protegidos.exchanges().contains(nombre)) {
            throw new ReglaNegocioException("El exchange '" + nombre + "' es parte de la topología base del sistema y no se puede eliminar");
        }
        if (management.exchange(nombre).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el exchange '" + nombre + "'");
        }
        amqpAdmin.deleteExchange(nombre);
        log.info("[ADMIN] exchange eliminado: {}", nombre);
    }

    // ============================== Bindings ==============================

    public List<BindingInfo> listarBindings() {
        return management.bindings().stream()
                .map(b -> new BindingInfo(texto(b, "source"), texto(b, "destination"),
                        texto(b, "destination_type"), texto(b, "routing_key")))
                .filter(b -> !b.exchange().isEmpty()) // el binding implícito al default exchange no aporta
                .sorted(Comparator.comparing(BindingInfo::exchange).thenComparing(BindingInfo::destino))
                .toList();
    }

    public BindingInfo crearBinding(BindingRequest solicitud) {
        TipoExchange tipo = validarBinding(solicitud);
        String rk = solicitud.routingKeyNormalizada();
        amqpAdmin.declareBinding(new Binding(solicitud.cola(), Binding.DestinationType.QUEUE,
                solicitud.exchange(), rk, null));
        log.info("[ADMIN] binding creado: {} ({}) -> {} rk='{}'", solicitud.exchange(), tipo, solicitud.cola(), rk);
        return new BindingInfo(solicitud.exchange(), solicitud.cola(), "queue", rk);
    }

    public void eliminarBinding(BindingRequest solicitud) {
        validarBinding(solicitud);
        String rk = solicitud.routingKeyNormalizada();
        boolean existe = management.bindings().stream().anyMatch(b ->
                solicitud.exchange().equals(texto(b, "source")) && solicitud.cola().equals(texto(b, "destination"))
                        && rk.equals(texto(b, "routing_key")));
        if (!existe) {
            throw new RecursoNoEncontradoException("No existe el binding " + solicitud.exchange() + " -> "
                    + solicitud.cola() + " con routing key '" + rk + "'");
        }
        amqpAdmin.removeBinding(new Binding(solicitud.cola(), Binding.DestinationType.QUEUE,
                solicitud.exchange(), rk, null));
        log.info("[ADMIN] binding eliminado: {} -> {} rk='{}'", solicitud.exchange(), solicitud.cola(), rk);
    }

    private TipoExchange validarBinding(BindingRequest solicitud) {
        validador.validarNombre(solicitud.exchange(), "el exchange");
        validador.validarNombre(solicitud.cola(), "la cola");
        Map<String, Object> exchange = management.exchange(solicitud.exchange())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el exchange '" + solicitud.exchange() + "'"));
        if (amqpAdmin.getQueueInfo(solicitud.cola()) == null) {
            throw new RecursoNoEncontradoException("No existe la cola '" + solicitud.cola() + "'");
        }
        TipoExchange tipo = TipoExchange.desdeAmqp(texto(exchange, "type"));
        validador.validarRoutingKey(tipo, solicitud.routingKeyNormalizada());
        return tipo;
    }

    // ===================== Políticas de retención (en caliente) =====================

    public List<PoliticaInfo> listarPoliticas() {
        return management.politicas().stream().map(RabbitAdminService::aPoliticaInfo)
                .sorted(Comparator.comparing(PoliticaInfo::nombre)).toList();
    }

    /**
     * Crea o reemplaza una política. Se aplica de inmediato a las colas existentes y futuras cuyo
     * nombre calce con el patrón, sin recrearlas (los argumentos x-* en cambio son inmutables).
     */
    public PoliticaInfo guardarPolitica(String nombre, PoliticaRequest solicitud) {
        validador.validarNombre(nombre, "la política");
        validador.validarPolitica(solicitud);
        Map<String, Object> definicion = new LinkedHashMap<>();
        if (solicitud.ttlMs() != null) {
            definicion.put("message-ttl", solicitud.ttlMs());
        }
        if (solicitud.maxMensajes() != null) {
            definicion.put("max-length", solicitud.maxMensajes());
        }
        if (solicitud.desborde() != null) {
            definicion.put("overflow", solicitud.desborde().valor());
        }
        if (solicitud.expiraSinUsoMs() != null) {
            definicion.put("expires", solicitud.expiraSinUsoMs());
        }
        Map<String, Object> cuerpo = Map.of("pattern", solicitud.patron(), "definition", definicion,
                "priority", solicitud.prioridadEfectiva(), "apply-to", "queues");
        management.guardarPolitica(nombre, cuerpo);
        log.info("[ADMIN] política '{}' aplicada a colas '{}': {}", nombre, solicitud.patron(), definicion);
        return new PoliticaInfo(nombre, solicitud.patron(), "queues", solicitud.prioridadEfectiva(), definicion);
    }

    public void eliminarPolitica(String nombre) {
        validador.validarNombre(nombre, "la política");
        if (management.politica(nombre).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la política '" + nombre + "'");
        }
        management.eliminarPolitica(nombre);
        log.info("[ADMIN] política eliminada: {}", nombre);
    }

    // =============================== Clúster y salud ===============================

    public List<NodoInfo> listarNodos() {
        return management.nodos().stream()
                .map(n -> new NodoInfo(texto(n, "name"), Boolean.TRUE.equals(n.get("running")), texto(n, "type"),
                        numero(n, "mem_used"), numero(n, "mem_limit"), numero(n, "uptime"),
                        Boolean.TRUE.equals(n.get("mem_alarm")), Boolean.TRUE.equals(n.get("disk_free_alarm"))))
                .sorted(Comparator.comparing(NodoInfo::nombre))
                .toList();
    }

    /** Totales, tasas globales y alertas que explican el impacto en rendimiento y estabilidad. */
    public SaludMensajeria salud() {
        Map<String, Object> overview = management.overview();
        Map<String, Object> stats = mapa(overview.get("message_stats"));
        return analizador.analizar(listarColas(), listarNodos(), tasa(stats, "publish_details"),
                tasa(stats, "deliver_get_details"), tasa(stats, "ack_details"), prefetch);
    }

    // ============================== Mapeos ==============================

    private ColaInfo aColaInfo(Map<String, Object> q) {
        String nombre = texto(q, "name");
        Map<String, Object> argumentos = mapa(q.get("arguments"));
        Map<String, Object> politica = mapa(q.get("effective_policy_definition"));
        Map<String, Object> stats = mapa(q.get("message_stats"));
        long mensajes = numero(q, "messages");
        RetencionInfo retencion = retencion(argumentos, politica, texto(q, "policy"));
        Double ocupacion = retencion.maxMensajes() != null && retencion.maxMensajes() > 0
                ? Math.round(mensajes * 1000.0 / retencion.maxMensajes()) / 10.0
                : null;
        Double utilizacion = q.get("consumer_utilisation") instanceof Number n ? n.doubleValue() : null;
        String politicaNombre = texto(q, "policy");
        return new ColaInfo(nombre, texto(q, "type"), Boolean.TRUE.equals(q.get("durable")),
                Boolean.TRUE.equals(q.get("auto_delete")), texto(q, "node"), texto(q, "state"),
                mensajes, numero(q, "messages_ready"), numero(q, "messages_unacknowledged"),
                (int) numero(q, "consumers"), utilizacion,
                tasa(stats, "publish_details"), tasa(stats, "deliver_get_details"), tasa(stats, "ack_details"),
                numero(q, "memory"), nombre.endsWith(SUFIJO_DLQ), protegidos.colas().contains(nombre),
                politicaNombre.isEmpty() ? null : politicaNombre, retencion, ocupacion, argumentos);
    }

    /** TTL y largo máximo: rige el menor entre argumento y política. Desborde y expiración: el argumento. */
    static RetencionInfo retencion(Map<String, Object> argumentos, Map<String, Object> politica, String nombrePolitica) {
        Long ttl = menor(largo(argumentos.get("x-message-ttl")), largo(politica.get("message-ttl")));
        Long max = menor(largo(argumentos.get("x-max-length")), largo(politica.get("max-length")));
        Object desborde = argumentos.getOrDefault("x-overflow", politica.get("overflow"));
        Long expira = largo(argumentos.get("x-expires")) != null
                ? largo(argumentos.get("x-expires")) : largo(politica.get("expires"));
        boolean deArgumentos = argumentos.keySet().stream().anyMatch(k -> k.equals("x-message-ttl")
                || k.equals("x-max-length") || k.equals("x-overflow") || k.equals("x-expires"));
        boolean dePolitica = !politica.isEmpty();
        String origen = deArgumentos && dePolitica ? "argumentos + política " + nombrePolitica
                : deArgumentos ? "argumentos de la cola"
                : dePolitica ? "política " + nombrePolitica
                : "sin límites";
        return new RetencionInfo(ttl, max, desborde != null ? desborde.toString() : (max != null ? "drop-head" : null),
                expira, origen);
    }

    private ExchangeInfo aExchangeInfo(Map<String, Object> e) {
        String nombre = texto(e, "name");
        return new ExchangeInfo(nombre, texto(e, "type").toUpperCase(), Boolean.TRUE.equals(e.get("durable")),
                Boolean.TRUE.equals(e.get("auto_delete")), protegidos.exchanges().contains(nombre));
    }

    private static PoliticaInfo aPoliticaInfo(Map<String, Object> p) {
        return new PoliticaInfo(texto(p, "name"), texto(p, "pattern"), texto(p, "apply-to"),
                (int) numero(p, "priority"), mapa(p.get("definition")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return valor instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static double tasa(Map<String, Object> stats, String clave) {
        Object rate = mapa(stats.get(clave)).get("rate");
        return rate instanceof Number n ? Math.round(n.doubleValue() * 10) / 10.0 : 0.0;
    }

    private static Long largo(Object valor) {
        return valor instanceof Number n ? n.longValue() : null;
    }

    private static Long menor(Long a, Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.min(a, b);
    }

    private static String texto(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor != null ? valor.toString() : "";
    }

    private static long numero(Map<String, Object> mapa, String clave) {
        return mapa.get(clave) instanceof Number n ? n.longValue() : 0L;
    }
}
