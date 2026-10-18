package cl.duoc.pedidos.common.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacionJwtTest {

    private static final String ISS_ENTRA = "https://tenant.ciamlogin.com/tenant/v2.0";
    private static final String ISS_COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_Abc";

    private final SeguridadProperties.Proveedor entra =
            new SeguridadProperties.Proveedor(ISS_ENTRA, null, List.of("client-entra"), "aud");
    private final SeguridadProperties.Proveedor cognito =
            new SeguridadProperties.Proveedor(ISS_COGNITO, null, List.of("client-cognito"), "client_id");

    @Test
    void tokenDeEntraConAudienciaCorrectaEsValido() {
        Jwt token = token(ISS_ENTRA, Map.of("aud", List.of("client-entra")), Instant.now().plusSeconds(600));
        assertThat(validar(entra, token)).isTrue();
    }

    @Test
    void tokenDeEntraParaOtraApiEsRechazado() {
        Jwt token = token(ISS_ENTRA, Map.of("aud", List.of("otra-api")), Instant.now().plusSeconds(600));
        assertThat(validar(entra, token)).isFalse();
    }

    @Test
    void tokenDeCognitoSeValidaPorClientId() {
        Jwt token = token(ISS_COGNITO, Map.of("client_id", "client-cognito", "token_use", "access"),
                Instant.now().plusSeconds(600));
        assertThat(validar(cognito, token)).isTrue();
    }

    @Test
    void tokenDeCognitoDeOtroClienteEsRechazado() {
        Jwt token = token(ISS_COGNITO, Map.of("client_id", "intruso"), Instant.now().plusSeconds(600));
        assertThat(validar(cognito, token)).isFalse();
    }

    @Test
    void tokenExpiradoEsRechazado() {
        Jwt token = token(ISS_ENTRA, Map.of("aud", List.of("client-entra")), Instant.now().minusSeconds(600));
        assertThat(validar(entra, token)).isFalse();
    }

    @Test
    void tokenConEmisorDistintoEsRechazado() {
        Jwt token = token(ISS_COGNITO, Map.of("aud", List.of("client-entra")), Instant.now().plusSeconds(600));
        assertThat(validar(entra, token)).isFalse();
    }

    @Test
    void rolesDeEntraYGruposDeCognitoSeConviertenEnRoles() {
        Jwt entraAdmin = token(ISS_ENTRA, Map.of("roles", List.of("Admin")), Instant.now().plusSeconds(600));
        Jwt cognitoAdmin = token(ISS_COGNITO, Map.of("cognito:groups", List.of("Admin")), Instant.now().plusSeconds(600));

        var convertidor = SeguridadAutoConfiguration.convertidorAutenticacion();
        assertThat(convertidor.convert(entraAdmin).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_Admin");
        assertThat(convertidor.convert(cognitoAdmin).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_Admin");
    }

    @Test
    void soloSeConsideranProveedoresConIssuer() {
        SeguridadProperties props = new SeguridadProperties(true,
                Map.of("entra", entra, "cognito", new SeguridadProperties.Proveedor("", null, List.of(), null)),
                null, List.of());
        assertThat(props.proveedoresConfigurados()).containsOnlyKeys("entra");
    }

    private static boolean validar(SeguridadProperties.Proveedor proveedor, Jwt token) {
        OAuth2TokenValidator<Jwt> validador = SeguridadAutoConfiguration.validadores(proveedor);
        return !validador.validate(token).hasErrors();
    }

    private static Jwt token(String issuer, Map<String, Object> claims, Instant expira) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("usuario-1")
                .issuedAt(expira.minusSeconds(3600))
                .expiresAt(expira)
                .claims(c -> c.putAll(claims))
                .build();
    }
}
