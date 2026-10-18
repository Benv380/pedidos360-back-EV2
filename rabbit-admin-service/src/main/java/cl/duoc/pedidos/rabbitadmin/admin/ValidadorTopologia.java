package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.common.web.SolicitudInvalidaException;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearColaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoCola;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoDesborde;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoExchange;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validaciones de configuración que dependen de varios campos o del tipo de exchange
 * (las de formato están en los DTO con Bean Validation).
 */
@Component
public class ValidadorTopologia {

    private static final String PREFIJO_RESERVADO = "amq.";

    public void validarNombre(String nombre, String recurso) {
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalidaException("El nombre de " + recurso + " no puede estar vacío");
        }
        if (nombre.startsWith(PREFIJO_RESERVADO)) {
            throw new SolicitudInvalidaException("El prefijo 'amq.' está reservado por RabbitMQ (" + recurso + ")");
        }
    }

    public void validarCola(CrearColaRequest solicitud) {
        validarNombre(solicitud.nombre(), "la cola");
        if (solicitud.tipoEfectivo() == TipoCola.QUORUM && (!solicitud.esDurable() || solicitud.esAutoDelete())) {
            throw new SolicitudInvalidaException("Una cola QUORUM debe ser durable y no puede ser auto-delete");
        }
        if (solicitud.requiereDlq() && solicitud.nombre().endsWith(".dlq")) {
            throw new SolicitudInvalidaException("Una DLQ no puede tener a su vez otra DLQ ('" + solicitud.nombre() + "')");
        }
        if (solicitud.requiereDlq() && solicitud.esAutoDelete()) {
            throw new SolicitudInvalidaException("Una cola con DLQ no puede ser auto-delete: se perderían los mensajes rechazados");
        }
        validarDesborde(solicitud.desborde(), solicitud.maxMensajes(), solicitud.tipoEfectivo());
        if (solicitud.desborde() == TipoDesborde.REJECT_PUBLISH_DLX && !solicitud.requiereDlq()) {
            throw new SolicitudInvalidaException("El desborde REJECT_PUBLISH_DLX envía los mensajes rechazados a la DLQ: "
                    + "active conDlq");
        }
    }

    /** Una política debe tener un patrón regex válido y al menos un límite de retención. */
    public void validarPolitica(PoliticaRequest solicitud) {
        try {
            Pattern.compile(solicitud.patron());
        } catch (PatternSyntaxException e) {
            throw new SolicitudInvalidaException("El patrón '" + solicitud.patron() + "' no es una expresión regular válida");
        }
        if (solicitud.patron().equals(".*") || solicitud.patron().equals("^.*$") || solicitud.patron().equals(".+")) {
            throw new SolicitudInvalidaException("El patrón aplicaría a TODAS las colas (incluidas las de la topología "
                    + "base); use un patrón específico, ej: ^demo\\.");
        }
        if (solicitud.ttlMs() == null && solicitud.maxMensajes() == null && solicitud.expiraSinUsoMs() == null) {
            throw new SolicitudInvalidaException("La política debe definir al menos un límite: ttlMs, maxMensajes o expiraSinUsoMs");
        }
        validarDesborde(solicitud.desborde(), solicitud.maxMensajes(), TipoCola.CLASSIC);
    }

    private static void validarDesborde(TipoDesborde desborde, Integer maxMensajes, TipoCola tipoCola) {
        if (desborde != null && maxMensajes == null) {
            throw new SolicitudInvalidaException("El desborde solo aplica si se define maxMensajes");
        }
        if (desborde == TipoDesborde.REJECT_PUBLISH_DLX && tipoCola == TipoCola.QUORUM) {
            throw new SolicitudInvalidaException("Las colas QUORUM no soportan REJECT_PUBLISH_DLX; use REJECT_PUBLISH o DROP_HEAD");
        }
    }

    /**
     * Reglas de la routing key según el tipo de exchange:
     * DIRECT exige una clave exacta (sin comodines); TOPIC exige un patrón de palabras separadas por punto,
     * donde '*' y '#' solo pueden ser palabras completas; FANOUT ignora la clave, por lo que debe ir vacía.
     */
    public void validarRoutingKey(TipoExchange tipo, String routingKey) {
        if (tipo == null) {
            throw new SolicitudInvalidaException("Tipo de exchange no soportado para bindings");
        }
        switch (tipo) {
            case DIRECT -> {
                if (routingKey.isEmpty()) {
                    throw new SolicitudInvalidaException("Un exchange DIRECT requiere routing key");
                }
                if (routingKey.contains("*") || routingKey.contains("#")) {
                    throw new SolicitudInvalidaException("Un exchange DIRECT no admite comodines (* o #) en la routing key");
                }
            }
            case TOPIC -> {
                if (routingKey.isEmpty()) {
                    throw new SolicitudInvalidaException("Un exchange TOPIC requiere un patrón de routing key (ej: pedido.*)");
                }
                for (String palabra : routingKey.split("\\.", -1)) {
                    if (palabra.isEmpty()) {
                        throw new SolicitudInvalidaException("La routing key '" + routingKey + "' tiene palabras vacías (puntos seguidos o en los extremos)");
                    }
                    if ((palabra.contains("*") || palabra.contains("#")) && palabra.length() > 1) {
                        throw new SolicitudInvalidaException("En TOPIC, '*' y '#' deben ser una palabra completa (ej: pedido.*.creado)");
                    }
                }
            }
            case FANOUT -> {
                if (!routingKey.isEmpty()) {
                    throw new SolicitudInvalidaException("Un exchange FANOUT ignora la routing key: envíela vacía");
                }
            }
            case HEADERS -> throw new SolicitudInvalidaException(
                    "Los bindings a exchanges HEADERS requieren argumentos y no se administran desde esta API");
        }
    }
}
