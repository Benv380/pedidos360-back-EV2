package cl.duoc.pedidos.rabbitadmin.admin.dto;

import java.util.List;

/** Resumen operativo de la mensajería: totales, tasas globales y alertas con su impacto. */
public record SaludMensajeria(
        int colasActivas,
        int dlqs,
        long mensajesListos,
        long mensajesSinAck,
        long mensajesEnDlq,
        int consumidores,
        double tasaPublicacion,
        double tasaEntrega,
        double tasaAck,
        int nodosActivos,
        int nodosTotales,
        int prefetch,
        List<Alerta> alertas) {
}
