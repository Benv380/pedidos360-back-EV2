package cl.duoc.pedidos.pedidos.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String cliente;

    @Column(name = "email_cliente", nullable = false, length = 160)
    private String emailCliente;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(name = "creado_por", nullable = false, length = 160)
    private String creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    protected Pedido() {
    }

    public Pedido(String cliente, String emailCliente, String descripcion, BigDecimal total, String creadoPor) {
        this.cliente = cliente;
        this.emailCliente = emailCliente;
        this.descripcion = descripcion;
        this.total = total;
        this.creadoPor = creadoPor;
        this.estado = EstadoPedido.CREADO;
    }

    @PrePersist
    void alCrear() {
        fechaCreacion = Instant.now();
        fechaActualizacion = fechaCreacion;
    }

    @PreUpdate
    void alActualizar() {
        fechaActualizacion = Instant.now();
    }

    public void cambiarEstado(EstadoPedido nuevo) {
        this.estado = nuevo;
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getEmailCliente() {
        return emailCliente;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }
}
