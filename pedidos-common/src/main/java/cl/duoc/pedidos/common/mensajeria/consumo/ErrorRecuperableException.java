package cl.duoc.pedidos.common.mensajeria.consumo;

/**
 * Error transitorio (servicio externo caído, timeout, etc.).
 * El mensaje se reintenta hasta mensajeria.reintentos.maximo veces y luego va a la DLQ.
 */
public class ErrorRecuperableException extends RuntimeException {

    public ErrorRecuperableException(String mensaje) {
        super(mensaje);
    }

    public ErrorRecuperableException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
