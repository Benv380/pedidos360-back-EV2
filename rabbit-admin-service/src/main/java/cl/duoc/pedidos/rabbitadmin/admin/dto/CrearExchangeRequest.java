package cl.duoc.pedidos.rabbitadmin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(min = 3, max = 100, message = "El nombre del exchange debe tener entre 3 y 100 caracteres")
        @Pattern(regexp = NombresRabbit.PATRON_NOMBRE, message = "El nombre del exchange " + NombresRabbit.MENSAJE_NOMBRE)
        String nombre,

        @NotNull(message = "El tipo de exchange es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        TipoExchange tipo,

        Boolean durable,

        Boolean autoDelete) {

    public boolean esDurable() {
        return durable == null || durable;
    }

    public boolean esAutoDelete() {
        return autoDelete != null && autoDelete;
    }
}
