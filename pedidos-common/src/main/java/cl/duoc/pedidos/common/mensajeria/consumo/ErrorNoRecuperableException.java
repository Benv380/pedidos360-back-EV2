package cl.duoc.pedidos.common.mensajeria.consumo;

/**
 * Error permanente (datos inválidos, regla de negocio violada).
 * Reintentar no sirve: el mensaje se rechaza (NACK sin requeue) y va directo a la DLQ.
 */
public class ErrorNoRecuperableException extends RuntimeException {

    public ErrorNoRecuperableException(String mensaje) {
        super(mensaje);
    }
}
