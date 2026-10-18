package cl.duoc.pedidos.common.web;

/** Parámetros de entrada inválidos detectados por validaciones de negocio. Se traduce a HTTP 400. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
