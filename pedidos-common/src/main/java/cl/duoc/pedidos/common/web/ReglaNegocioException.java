package cl.duoc.pedidos.common.web;

/** Operación válida en forma pero no permitida por una regla de negocio. Se traduce a HTTP 409. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
