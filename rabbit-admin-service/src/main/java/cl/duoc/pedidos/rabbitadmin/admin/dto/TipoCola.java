package cl.duoc.pedidos.rabbitadmin.admin.dto;

public enum TipoCola {
    /** Cola clásica: vive en un nodo del clúster. */
    CLASSIC,
    /** Cola quorum: replicada entre los nodos del clúster (Raft). Debe ser durable. */
    QUORUM;

    public String argumento() {
        return name().toLowerCase();
    }
}
