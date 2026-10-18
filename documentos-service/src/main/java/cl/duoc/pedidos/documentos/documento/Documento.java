package cl.duoc.pedidos.documentos.documento;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documentos")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id del evento de origen: evita generar dos veces el mismo documento (idempotencia). */
    @Column(name = "evento_id", nullable = false, unique = true)
    private UUID eventoId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDocumento tipo;

    @Column(nullable = false, unique = true, length = 40)
    private String folio;

    @JsonIgnore
    @Column(nullable = false, length = 4000)
    private String contenido;

    @Column(name = "fecha_generacion", nullable = false)
    private Instant fechaGeneracion;

    protected Documento() {
    }

    public Documento(UUID eventoId, Long pedidoId, TipoDocumento tipo, String folio, String contenido) {
        this.eventoId = eventoId;
        this.pedidoId = pedidoId;
        this.tipo = tipo;
        this.folio = folio;
        this.contenido = contenido;
        this.fechaGeneracion = Instant.now();
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

    public TipoDocumento getTipo() {
        return tipo;
    }

    public String getFolio() {
        return folio;
    }

    public String getContenido() {
        return contenido;
    }

    public Instant getFechaGeneracion() {
        return fechaGeneracion;
    }
}
