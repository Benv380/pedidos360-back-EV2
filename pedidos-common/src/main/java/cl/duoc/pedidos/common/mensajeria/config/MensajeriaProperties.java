package cl.duoc.pedidos.common.mensajeria.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

/**
 * Nombres de exchanges, colas, DLQ y routing keys leídos desde mensajeria.yml.
 * Es la única fuente de nombres de mensajería para todos los microservicios.
 */
@Validated
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull Colas colas,
        @Valid @NotNull RoutingKeys routingKeys,
        @Valid @NotNull Reintentos reintentos,
        @Valid @NotNull Retencion retencion) {

    public record Exchanges(
            @NotBlank String eventosFanout,
            @NotBlank String estadosTopic,
            @NotBlank String documentosDirect,
            @NotBlank String deadLetter) {
    }

    /** Una cola de trabajo y su Dead Letter Queue asociada. */
    public record Cola(@NotBlank String nombre, @NotBlank String dlq) {
    }

    public record Colas(
            @Valid @NotNull Cola notificaciones,
            @Valid @NotNull Cola documentos,
            @Valid @NotNull Cola auditoria) {
    }

    public record RoutingKeys(
            @NotBlank String estadoPrefijo,
            @NotBlank String estadosNotificables,
            @NotBlank String todosLosPedidos,
            @NotBlank String documentoBoleta) {

        /** Routing key concreta para un cambio de estado. Ej: PAGADO -> pedido.estado.pagado */
        public String paraEstado(String estado) {
            return estadoPrefijo + "." + estado.toLowerCase(Locale.ROOT);
        }
    }

    public record Reintentos(@Min(0) int maximo, @Min(0) long esperaMs) {
    }

    /** Políticas de retención para las colas de trabajo y para las DLQ. */
    public record Retencion(@Valid @NotNull Limites colas, @Valid @NotNull Limites dlq) {
    }

    /**
     * @param ttlMs       tiempo máximo que un mensaje puede esperar en la cola (x-message-ttl)
     * @param maxMensajes largo máximo de la cola (x-max-length)
     * @param desborde    comportamiento al llegar al máximo (x-overflow)
     */
    public record Limites(
            @Min(1000) @Max(Integer.MAX_VALUE) long ttlMs,
            @Min(1) int maxMensajes,
            @NotBlank @Pattern(regexp = "drop-head|reject-publish|reject-publish-dlx",
                    message = "desborde debe ser drop-head, reject-publish o reject-publish-dlx") String desborde) {
    }
}
