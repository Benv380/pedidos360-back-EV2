package cl.duoc.pedidos.rabbitadmin.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de creación de cola. Los campos opcionales tienen valores por defecto seguros
 * (durable, sin auto-delete, clásica, sin DLQ).
 */
public record CrearColaRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre de la cola debe tener entre 3 y 100 caracteres")
        @Pattern(regexp = NombresRabbit.PATRON_NOMBRE, message = "El nombre de la cola " + NombresRabbit.MENSAJE_NOMBRE)
        String nombre,

        TipoCola tipo,

        Boolean durable,

        Boolean autoDelete,

        /* Si es true, se crea además <nombre>.dlq enlazada al dead-letter exchange del sistema. */
        Boolean conDlq,

        /* Retención: tiempo máximo de espera de cada mensaje (x-message-ttl). */
        @Min(value = 1000, message = "El TTL mínimo es 1000 ms")
        @Max(value = 604_800_000, message = "El TTL máximo es 7 días (604800000 ms)")
        Long ttlMs,

        /* Retención: largo máximo de la cola (x-max-length). */
        @Min(value = 1, message = "El largo máximo de la cola debe ser al menos 1")
        @Max(value = 1_000_000, message = "El largo máximo de la cola no puede superar 1.000.000 mensajes")
        Integer maxMensajes,

        /* Qué hacer al llegar al largo máximo (x-overflow). Requiere maxMensajes. */
        TipoDesborde desborde,

        /* Limpieza: elimina la cola si no se usa durante este tiempo (x-expires). */
        @Min(value = 10_000, message = "La expiración por inactividad mínima es 10000 ms")
        @Max(value = 2_592_000_000L, message = "La expiración por inactividad máxima es 30 días")
        Long expiraSinUsoMs) {

    public TipoCola tipoEfectivo() {
        return tipo != null ? tipo : TipoCola.CLASSIC;
    }

    public boolean esDurable() {
        return durable == null || durable;
    }

    public boolean esAutoDelete() {
        return autoDelete != null && autoDelete;
    }

    public boolean requiereDlq() {
        return conDlq != null && conDlq;
    }
}
