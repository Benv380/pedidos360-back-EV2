package cl.duoc.pedidos.notificaciones.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Registro inmutable de cada evento de pedido recibido, indicando por qué exchange llegó. */
@Entity
@Table(name = "eventos_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(length = 20)
    private String estado;

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(length = 160)
    private String usuario;

    @Column(length = 120)
    private String exchange;

    @Column(name = "routing_key", length = 120)
    private String routingKey;

    @Column(name = "fecha_evento")
    private Instant fechaEvento;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(UUID eventoId, String tipo, Long pedidoId, String estado, String estadoAnterior,
                           String usuario, String exchange, String routingKey, Instant fechaEvento) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.pedidoId = pedidoId;
        this.estado = estado;
        this.estadoAnterior = estadoAnterior;
        this.usuario = usuario;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.fechaEvento = fechaEvento;
        this.fechaRegistro = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoAnterior() {
        return estadoAnterior;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public Instant getFechaEvento() {
        return fechaEvento;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }
}
