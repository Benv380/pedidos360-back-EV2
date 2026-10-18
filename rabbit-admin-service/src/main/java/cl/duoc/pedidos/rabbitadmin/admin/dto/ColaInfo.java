package cl.duoc.pedidos.rabbitadmin.admin.dto;

import java.util.Map;

/**
 * Estado operativo de una cola.
 *
 * @param mensajesListos          pendientes de entregar (Ready): el backlog
 * @param mensajesSinAck          entregados a un consumidor y aún sin confirmar (en vuelo)
 * @param utilizacionConsumidores fracción de tiempo (0..1) en que la cola pudo entregar de inmediato
 *                                a sus consumidores; bajo = consumidores lentos o prefetch insuficiente
 * @param tasaPublicacion         mensajes/s que entran
 * @param tasaEntrega             mensajes/s entregados a consumidores
 * @param tasaAck                 mensajes/s confirmados
 * @param ocupacionPct            mensajes / largo máximo efectivo (null si no tiene límite)
 */
public record ColaInfo(
        String nombre,
        String tipo,
        boolean durable,
        boolean autoDelete,
        String nodo,
        String estado,
        long mensajes,
        long mensajesListos,
        long mensajesSinAck,
        int consumidores,
        Double utilizacionConsumidores,
        double tasaPublicacion,
        double tasaEntrega,
        double tasaAck,
        long memoriaBytes,
        boolean esDlq,
        boolean protegida,
        String politica,
        RetencionInfo retencion,
        Double ocupacionPct,
        Map<String, Object> argumentos) {
}
