package cl.duoc.pedidos.documentos.documento;

public enum TipoDocumento {
    /** Comprobante de recepción, generado al crear el pedido. */
    TICKET,
    /** Boleta de venta, generada a solicitud para pedidos pagados. */
    BOLETA
}
