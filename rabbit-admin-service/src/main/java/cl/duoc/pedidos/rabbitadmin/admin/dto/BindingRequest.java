package cl.duoc.pedidos.rabbitadmin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Enlace exchange -> cola. La validez de la routing key según el tipo de exchange la revisa el servicio. */
public record BindingRequest(
        @NotBlank(message = "El exchange es obligatorio")
        @Pattern(regexp = NombresRabbit.PATRON_NOMBRE, message = "El exchange " + NombresRabbit.MENSAJE_NOMBRE)
        String exchange,

        @NotBlank(message = "La cola es obligatoria")
        @Pattern(regexp = NombresRabbit.PATRON_NOMBRE, message = "La cola " + NombresRabbit.MENSAJE_NOMBRE)
        String cola,

        @Size(max = 255, message = "La routing key no puede superar 255 caracteres")
        @Pattern(regexp = NombresRabbit.PATRON_ROUTING_KEY, message = "La routing key " + NombresRabbit.MENSAJE_ROUTING_KEY)
        String routingKey) {

    public String routingKeyNormalizada() {
        return routingKey == null ? "" : routingKey.trim();
    }
}
