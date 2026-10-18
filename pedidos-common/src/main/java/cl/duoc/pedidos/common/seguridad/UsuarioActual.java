package cl.duoc.pedidos.common.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Obtiene el nombre del usuario autenticado a partir del JWT validado. */
public final class UsuarioActual {

    private static final String ANONIMO = "anonimo";

    private UsuarioActual() {
    }

    public static String nombre() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return ANONIMO;
        }
        // Entra: preferred_username / email / name · Cognito (access token): username
        for (String claim : new String[]{"preferred_username", "email", "name", "username", "cognito:username"}) {
            String valor = jwt.getClaimAsString(claim);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return jwt.getSubject() != null ? jwt.getSubject() : ANONIMO;
    }
}
