package cl.duoc.pedidos.common.mensajeria.evento;

public enum TipoEvento {
    /** Publicado en el exchange FANOUT. */
    PEDIDO_CREADO,
    /** Publicado en el exchange TOPIC con routing key pedido.estado.&lt;estado&gt;. */
    ESTADO_CAMBIADO,
    /** Publicado en el exchange DIRECT con routing key documento.boleta. */
    BOLETA_SOLICITADA
}
