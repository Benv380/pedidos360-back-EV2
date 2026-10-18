package cl.duoc.pedidos.rabbitadmin.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Política de RabbitMQ aplicada a las colas cuyo nombre calza con {@code patron} (expresión regular).
 * A diferencia de los argumentos x-*, una política se puede cambiar o quitar en caliente sin recrear la cola.
 */
public record PoliticaRequest(
        @NotBlank(message = "El patrón (regex) es obligatorio, ej: ^demo\\.")
        @Size(max = 200, message = "El patrón no puede superar 200 caracteres")
        String patron,

        @Min(value = 0, message = "La prioridad mínima es 0")
        @Max(value = 999, message = "La prioridad máxima es 999")
        Integer prioridad,

        @Min(value = 1000, message = "El TTL mínimo es 1000 ms")
        @Max(value = 604_800_000, message = "El TTL máximo es 7 días (604800000 ms)")
        Long ttlMs,

        @Min(value = 1, message = "El largo máximo debe ser al menos 1")
        @Max(value = 1_000_000, message = "El largo máximo no puede superar 1.000.000 mensajes")
        Integer maxMensajes,

        TipoDesborde desborde,

        @Min(value = 10_000, message = "La expiración por inactividad mínima es 10000 ms")
        @Max(value = 2_592_000_000L, message = "La expiración por inactividad máxima es 30 días")
        Long expiraSinUsoMs) {

    public int prioridadEfectiva() {
        return prioridad != null ? prioridad : 0;
    }
}
