package cl.duoc.pedidos.rabbitadmin.admin.dto;

/**
 * Límites de retención EFECTIVOS de una cola, combinando sus argumentos (x-*) y la política aplicada.
 * Para TTL y largo máximo rige el menor de ambos; para desborde y expiración, el argumento.
 *
 * @param ttlMs       tiempo máximo de espera de un mensaje (null = sin expiración)
 * @param maxMensajes largo máximo (null = ilimitado)
 * @param desborde    drop-head | reject-publish | reject-publish-dlx (por defecto drop-head)
 * @param expiraMs    la cola se elimina tras este tiempo sin uso (null = nunca)
 * @param origen      de dónde vienen los límites: argumentos, política o ambos
 */
public record RetencionInfo(Long ttlMs, Long maxMensajes, String desborde, Long expiraMs, String origen) {

    public boolean tieneLimites() {
        return ttlMs != null || maxMensajes != null || expiraMs != null;
    }
}
