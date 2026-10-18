package cl.duoc.pedidos.rabbitadmin.admin.dto;

/** Comportamiento de una cola al alcanzar su largo máximo (x-overflow / overflow). */
public enum TipoDesborde {
    /** Descarta el mensaje más antiguo para hacer espacio. */
    DROP_HEAD("drop-head"),
    /** Rechaza el mensaje nuevo: el productor recibe NACK (publisher confirms). */
    REJECT_PUBLISH("reject-publish"),
    /** Rechaza el mensaje nuevo y lo envía al dead-letter exchange (solo colas clásicas). */
    REJECT_PUBLISH_DLX("reject-publish-dlx");

    private final String valor;

    TipoDesborde(String valor) {
        this.valor = valor;
    }

    public String valor() {
        return valor;
    }
}
