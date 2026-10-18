package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.rabbitadmin.admin.dto.Alerta;
import cl.duoc.pedidos.rabbitadmin.admin.dto.Alerta.Nivel;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ColaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.NodoInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.SaludMensajeria;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interpreta las métricas de RabbitMQ y explica su impacto en rendimiento y estabilidad.
 * <pre>
 * Métrica                         Señal                                    Impacto
 * ───────────────────────────────  ──────────────────────────────────────  ─────────────────────────────────────────────
 * Nodo caído / alarma mem-disco    nodo !running, mem_alarm, disk_alarm    colas no disponibles / productores BLOQUEADOS
 * Pendientes sin consumidores      ready > 0 y consumers = 0               backlog crece -> TTL -> DLQ, uso de memoria
 * Ocupación vs largo máximo        mensajes / max-length ≥ 50 % / 80 %     al 100 % se aplica el desborde (DLQ/NACK/descarta)
 * Producción > consumo             publish/s > deliver/s con backlog       latencia creciente -> escalar consumidores
 * Prefetch saturado                unacked ≥ consumidores × prefetch       consumidores al máximo de mensajes en vuelo
 * Mensajes en DLQ                  dlq.messages > 0                        errores aislados que requieren revisión
 * Cola sin retención               sin TTL ni max-length                   puede crecer sin control
 * </pre>
 */
@Component
public class AnalizadorSalud {

    static final double OCUPACION_ADVERTENCIA = 50.0;
    static final double OCUPACION_CRITICA = 80.0;
    static final long BACKLOG_RELEVANTE = 50;

    public SaludMensajeria analizar(List<ColaInfo> colas, List<NodoInfo> nodos, double tasaPublicacion,
                                    double tasaEntrega, double tasaAck, int prefetch) {
        List<Alerta> alertas = new ArrayList<>();
        nodos.forEach(n -> analizarNodo(n, alertas));
        colas.forEach(c -> analizarCola(c, prefetch, alertas));
        if (alertas.isEmpty()) {
            alertas.add(new Alerta(Nivel.INFO, "sistema", "Sin alertas",
                    "Todas las colas tienen consumidores, están bajo sus límites y no hay mensajes fallidos."));
        }
        alertas.sort(Comparator.comparing(Alerta::nivel));

        List<ColaInfo> trabajo = colas.stream().filter(c -> !c.esDlq()).toList();
        List<ColaInfo> dlqs = colas.stream().filter(ColaInfo::esDlq).toList();
        return new SaludMensajeria(
                trabajo.size(), dlqs.size(),
                trabajo.stream().mapToLong(ColaInfo::mensajesListos).sum(),
                trabajo.stream().mapToLong(ColaInfo::mensajesSinAck).sum(),
                dlqs.stream().mapToLong(ColaInfo::mensajes).sum(),
                trabajo.stream().mapToInt(ColaInfo::consumidores).sum(),
                tasaPublicacion, tasaEntrega, tasaAck,
                (int) nodos.stream().filter(NodoInfo::activo).count(), nodos.size(), prefetch,
                List.copyOf(alertas));
    }

    private static void analizarNodo(NodoInfo n, List<Alerta> alertas) {
        if (!n.activo()) {
            alertas.add(new Alerta(Nivel.CRITICO, n.nombre(), "Nodo caído",
                    "Las colas clásicas alojadas en este nodo no están disponibles hasta que vuelva. "
                            + "Los microservicios reconectan automáticamente al otro nodo del clúster."));
        }
        if (n.alarmaMemoria() || n.alarmaDisco()) {
            alertas.add(new Alerta(Nivel.CRITICO, n.nombre(),
                    "Alarma de " + (n.alarmaMemoria() ? "memoria" : "disco"),
                    "RabbitMQ BLOQUEA a todos los productores (flow control) hasta liberar recursos. "
                            + "Causa típica: backlog sin límites. Por eso todas las colas tienen TTL y largo máximo."));
        }
    }

    private static void analizarCola(ColaInfo c, int prefetch, List<Alerta> alertas) {
        if (c.esDlq()) {
            if (c.mensajes() > 0) {
                alertas.add(new Alerta(Nivel.ADVERTENCIA, c.nombre(), c.mensajes() + " mensajes fallidos en la DLQ",
                        "No afectan el flujo normal porque están aislados, pero indican errores a revisar "
                                + "(logs [NACK -> DLQ]). Se eliminan solos al cumplir el TTL de la DLQ"
                                + ttl(c) + "; también se pueden reprocesar o purgar."));
            }
            return;
        }

        if (c.mensajesListos() > 0 && c.consumidores() == 0) {
            alertas.add(new Alerta(Nivel.CRITICO, c.nombre(),
                    "Sin consumidores con " + c.mensajesListos() + " mensajes pendientes",
                    "El backlog crece y ocupa memoria/disco del broker. Si nadie los procesa, expiran"
                            + ttl(c) + " y pasan a la DLQ. Reanudar o escalar el consumidor."));
        }

        if (c.ocupacionPct() != null && c.ocupacionPct() >= OCUPACION_ADVERTENCIA) {
            boolean critica = c.ocupacionPct() >= OCUPACION_CRITICA;
            alertas.add(new Alerta(critica ? Nivel.CRITICO : Nivel.ADVERTENCIA, c.nombre(),
                    String.format("Al %.0f %% de su largo máximo (%d de %d)", c.ocupacionPct(), c.mensajes(),
                            c.retencion().maxMensajes()),
                    "Al llegar al 100 %: " + efectoDesborde(c.retencion().desborde())));
        }

        if (c.mensajesListos() >= BACKLOG_RELEVANTE && c.tasaPublicacion() > c.tasaEntrega() * 1.2) {
            alertas.add(new Alerta(Nivel.ADVERTENCIA, c.nombre(),
                    String.format("Se publica más rápido de lo que se consume (%.1f/s vs %.1f/s)",
                            c.tasaPublicacion(), c.tasaEntrega()),
                    "La latencia de procesamiento aumenta con el backlog. Subir max-concurrency o agregar "
                            + "instancias del microservicio consumidor."));
        }

        if (c.consumidores() > 0 && prefetch > 0 && c.mensajesSinAck() >= (long) c.consumidores() * prefetch) {
            alertas.add(new Alerta(Nivel.INFO, c.nombre(),
                    "Consumidores con el prefetch completo (" + c.mensajesSinAck() + " sin ACK)",
                    "Cada consumidor tiene el máximo de mensajes en vuelo: el cuello de botella es el procesamiento. "
                            + "Un prefetch mayor no ayuda; sí más concurrencia."));
        }

        if (!c.retencion().tieneLimites() && !c.autoDelete()) {
            alertas.add(new Alerta(Nivel.INFO, c.nombre(), "Sin políticas de retención",
                    "Sin TTL ni largo máximo, la cola puede crecer sin control si su consumidor falla. "
                            + "Definir argumentos x-message-ttl / x-max-length o una política."));
        }
    }

    static String efectoDesborde(String desborde) {
        return switch (desborde == null ? "drop-head" : desborde) {
            case "reject-publish-dlx" -> "los mensajes NUEVOS se rechazan y se desvían a la DLQ (no se pierden).";
            case "reject-publish" -> "los mensajes NUEVOS se rechazan y el productor recibe NACK en el publisher confirm.";
            default -> "se DESCARTAN los mensajes más antiguos para hacer espacio (drop-head).";
        };
    }

    private static String ttl(ColaInfo c) {
        Long ttl = c.retencion().ttlMs();
        if (ttl == null) {
            return "";
        }
        if (ttl >= 86_400_000) {
            return " (TTL " + ttl / 86_400_000 + " d)";
        }
        return ttl >= 3_600_000 ? " (TTL " + ttl / 3_600_000 + " h)" : " (TTL " + ttl / 1000 + " s)";
    }
}
