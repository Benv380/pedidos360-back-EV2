package cl.duoc.pedidos.rabbitadmin.admin.dto;

/**
 * Hallazgo del análisis de salud de la mensajería.
 *
 * @param recurso cola o nodo afectado
 * @param mensaje qué está pasando
 * @param impacto efecto en rendimiento/estabilidad y qué hacer
 */
public record Alerta(Nivel nivel, String recurso, String mensaje, String impacto) {

    public enum Nivel { CRITICO, ADVERTENCIA, INFO }
}
