package cl.duoc.pedidos.rabbitadmin.admin.dto;

/** Reglas de formato compartidas por los DTO del administrador. */
public final class NombresRabbit {

    /** Letras, números, punto, guion y guion bajo; debe comenzar con letra o número. */
    public static final String PATRON_NOMBRE = "^[a-zA-Z0-9][a-zA-Z0-9._-]*$";
    public static final String MENSAJE_NOMBRE =
            "solo admite letras, números, '.', '-' y '_', y debe comenzar con letra o número";

    /** Palabras separadas por punto; admite comodines * y # (solo válidos en exchanges topic). */
    public static final String PATRON_ROUTING_KEY = "^[a-zA-Z0-9._*#-]*$";
    public static final String MENSAJE_ROUTING_KEY =
            "solo admite letras, números, '.', '-', '_' y los comodines '*' y '#'";

    private NombresRabbit() {
    }
}
