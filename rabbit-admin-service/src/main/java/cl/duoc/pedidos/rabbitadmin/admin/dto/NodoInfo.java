package cl.duoc.pedidos.rabbitadmin.admin.dto;

/**
 * Nodo del clúster RabbitMQ (tipo: disc o ram).
 *
 * @param alarmaMemoria true si el nodo superó su límite de memoria: RabbitMQ BLOQUEA a los productores
 * @param alarmaDisco   true si queda poco disco: también bloquea a los productores
 */
public record NodoInfo(String nombre, boolean activo, String tipo, long memoriaUsadaBytes, long memoriaLimiteBytes,
                       long uptimeMs, boolean alarmaMemoria, boolean alarmaDisco) {
}
