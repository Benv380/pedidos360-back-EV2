package cl.duoc.pedidos.common.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seguridad común de los microservicios (OAuth 2.0 Resource Server multi-IDaaS).
 * <p>
 * Cada petición pasa por el filtro {@code BearerTokenAuthenticationFilter}, que extrae el JWT del header
 * {@code Authorization: Bearer ...}. Según el claim {@code iss} se elige el proveedor de confianza
 * (Microsoft Entra External ID en Azure o Amazon Cognito en AWS) y se valida:
 * <ul>
 *   <li>firma (llaves públicas JWKS de ese IDaaS)</li>
 *   <li>expiración (exp / nbf)</li>
 *   <li>emisor (iss)</li>
 *   <li>audiencia: claim "aud" (Entra) o "client_id" (Cognito)</li>
 * </ul>
 * Un token de un emisor desconocido o que falle cualquier validación recibe 401 y no llega al controlador.
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@EnableWebSecurity
@EnableConfigurationProperties(SeguridadProperties.class)
public class SeguridadAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeguridadAutoConfiguration.class);

    @Bean
    SecurityFilterChain cadenaFiltrosSeguridad(
            HttpSecurity http, SeguridadProperties props,
            ObjectProvider<AuthenticationManagerResolver<HttpServletRequest>> resolvedorEmisores) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!props.estaHabilitada()) {
            log.warn("SEGURIDAD DESHABILITADA (seguridad.habilitada=false). Usar solo en desarrollo local.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
            auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
            if (StringUtils.hasText(props.rolAdmin())) {
                auth.requestMatchers("/api/admin/**").hasRole(props.rolAdmin());
            }
            auth.anyRequest().authenticated();
        });
        http.oauth2ResourceServer(rs -> rs.authenticationManagerResolver(resolvedorEmisores.getObject()));
        return http.build();
    }

    /**
     * Elige el validador según el emisor del token. Solo se aceptan los emisores configurados
     * en seguridad.proveedores; cualquier otro recibe 401 ("Invalid issuer").
     */
    @Bean
    @ConditionalOnProperty(prefix = "seguridad", name = "habilitada", havingValue = "true", matchIfMissing = true)
    AuthenticationManagerResolver<HttpServletRequest> resolvedorEmisores(SeguridadProperties props) {
        Map<String, AuthenticationManager> porEmisor = new HashMap<>();
        props.proveedoresConfigurados().forEach((nombre, proveedor) -> {
            JwtAuthenticationProvider autenticador = new JwtAuthenticationProvider(decodificador(proveedor));
            autenticador.setJwtAuthenticationConverter(convertidorAutenticacion());
            porEmisor.put(proveedor.issuer(), autenticador::authenticate);
            log.info("IDaaS de confianza '{}': issuer={} audiencia({})={}", nombre, proveedor.issuer(),
                    proveedor.claimAudienciaEfectiva(), proveedor.audienciasValidas());
        });
        if (porEmisor.isEmpty()) {
            throw new IllegalStateException("No hay IDaaS configurado. Define ENTRA_ISSUER y/o COGNITO_ISSUER, "
                    + "o usa SEGURIDAD_HABILITADA=false solo para desarrollo local.");
        }
        return new JwtIssuerAuthenticationManagerResolver(porEmisor::get);
    }

    /**
     * Decodificador del JWT de un proveedor. Se crea de forma perezosa (SupplierJwtDecoder) para que el
     * servicio arranque aunque el IDaaS no responda en ese instante.
     */
    static JwtDecoder decodificador(SeguridadProperties.Proveedor proveedor) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = StringUtils.hasText(proveedor.jwkSetUri())
                    ? NimbusJwtDecoder.withJwkSetUri(proveedor.jwkSetUri()).build()
                    : NimbusJwtDecoder.withIssuerLocation(proveedor.issuer()).build();
            decoder.setJwtValidator(validadores(proveedor));
            return decoder;
        });
    }

    static OAuth2TokenValidator<Jwt> validadores(SeguridadProperties.Proveedor proveedor) {
        List<OAuth2TokenValidator<Jwt>> lista = new ArrayList<>();
        lista.add(JwtValidators.createDefaultWithIssuer(proveedor.issuer())); // exp, nbf e iss
        if (!proveedor.audienciasValidas().isEmpty()) {
            lista.add(validadorAudiencia(proveedor.claimAudienciaEfectiva(), proveedor.audienciasValidas()));
        }
        return new DelegatingOAuth2TokenValidator<>(lista);
    }

    /** Verifica que el token fue emitido para esta API: el claim indicado debe contener una audiencia válida. */
    static OAuth2TokenValidator<Jwt> validadorAudiencia(String claim, List<String> validas) {
        return token -> {
            Object valor = token.getClaims().get(claim);
            boolean valido = valor instanceof Collection<?> lista
                    ? lista.stream().anyMatch(v -> validas.contains(String.valueOf(v)))
                    : valor != null && validas.contains(valor.toString());
            return valido
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                    "El token no fue emitido para esta API (" + claim + " inválido)", null));
        };
    }

    /**
     * Autoridades: scopes (scp/scope -> SCOPE_x) + App Roles de Entra (roles -> ROLE_x)
     * + grupos de Cognito (cognito:groups -> ROLE_x).
     */
    static JwtAuthenticationConverter convertidorAutenticacion() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(token -> {
            Collection<GrantedAuthority> autoridades = new ArrayList<>(scopes.convert(token));
            for (String claimRoles : List.of("roles", "cognito:groups")) {
                List<String> roles = token.getClaimAsStringList(claimRoles);
                if (roles != null) {
                    roles.forEach(r -> autoridades.add(new SimpleGrantedAuthority("ROLE_" + r)));
                }
            }
            autoridades.add(new SimpleGrantedAuthority("USUARIO_AUTENTICADO"));
            return autoridades;
        });
        convertidor.setPrincipalClaimName("sub");
        return convertidor;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SeguridadProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(props.origenesCors());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
