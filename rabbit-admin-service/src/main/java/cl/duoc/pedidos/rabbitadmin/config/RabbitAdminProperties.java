package cl.duoc.pedidos.rabbitadmin.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rabbit-admin")
public record RabbitAdminProperties(@NotBlank String managementUrl) {
}
