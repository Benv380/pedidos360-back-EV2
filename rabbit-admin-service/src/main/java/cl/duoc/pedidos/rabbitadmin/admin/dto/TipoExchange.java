package cl.duoc.pedidos.rabbitadmin.admin.dto;

import org.springframework.amqp.core.ExchangeTypes;

public enum TipoExchange {
    DIRECT(ExchangeTypes.DIRECT),
    TOPIC(ExchangeTypes.TOPIC),
    FANOUT(ExchangeTypes.FANOUT),
    HEADERS(ExchangeTypes.HEADERS);

    private final String tipoAmqp;

    TipoExchange(String tipoAmqp) {
        this.tipoAmqp = tipoAmqp;
    }

    public String tipoAmqp() {
        return tipoAmqp;
    }

    public static TipoExchange desdeAmqp(String tipo) {
        for (TipoExchange t : values()) {
            if (t.tipoAmqp.equalsIgnoreCase(tipo)) {
                return t;
            }
        }
        return null;
    }
}
