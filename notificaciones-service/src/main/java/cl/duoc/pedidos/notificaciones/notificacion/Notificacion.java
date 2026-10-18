package cl.duoc.pedidos.notificaciones.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id del evento de origen: evita enviar dos veces la misma notificación (idempotencia). */
    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(nullable = false, length = 160)
    private String destinatario;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Column(name = "fecha_envio", nullable = false)
    private Instant fechaEnvio;

    protected Notificacion() {
    }

    public Notificacion(UUID eventoId, Long pedidoId, String destinatario, String asunto, String mensaje) {
        this.eventoId = eventoId;
        this.pedidoId = pedidoId;
        this.destinatario = destinatario;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.fechaEnvio = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Instant getFechaEnvio() {
        return fechaEnvio;
    }
}
