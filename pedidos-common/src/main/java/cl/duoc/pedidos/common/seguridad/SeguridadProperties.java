package cl.duoc.pedidos.common.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Propiedades de seguridad leídas desde seguridad.yml. */
@ConfigurationProperties(prefix = "seguridad")
public record SeguridadProperties(
        Boolean habilitada,
        Map<String, Proveedor> proveedores,
        String rolAdmin,
        List<String> corsOrigenes) {

    /**
     * IDaaS de confianza.
     *
     * @param issuer          valor exacto del claim "iss" de sus tokens
     * @param jwkSetUri       opcional; si no se define se usa el discovery del issuer
     * @param audiencias      valores aceptados en el claim de audiencia
     * @param claimAudiencia  "aud" (Entra) o "client_id" (Cognito)
     */
    public record Proveedor(String issuer, String jwkSetUri, List<String> audiencias, String claimAudiencia) {

        public boolean configurado() {
            return StringUtils.hasText(issuer);
        }

        public String claimAudienciaEfectiva() {
            return StringUtils.hasText(claimAudiencia) ? claimAudiencia : "aud";
        }

        public List<String> audienciasValidas() {
            return audiencias == null ? List.of() : audiencias.stream().filter(StringUtils::hasText).toList();
        }
    }

    public boolean estaHabilitada() {
        return habilitada == null || habilitada;
    }

    /** Proveedores con issuer definido (los demás se ignoran). */
    public Map<String, Proveedor> proveedoresConfigurados() {
        Map<String, Proveedor> resultado = new LinkedHashMap<>();
        if (proveedores != null) {
            proveedores.forEach((nombre, p) -> {
                if (p != null && p.configurado()) {
                    resultado.put(nombre, p);
                }
            });
        }
        return resultado;
    }

    public List<String> origenesCors() {
        return corsOrigenes == null ? List.of() : corsOrigenes.stream().filter(StringUtils::hasText).toList();
    }
}
