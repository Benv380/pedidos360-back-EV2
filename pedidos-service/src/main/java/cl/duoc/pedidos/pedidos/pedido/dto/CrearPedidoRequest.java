package cl.duoc.pedidos.pedidos.pedido.dto;

import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CrearPedidoRequest(
        @NotBlank(message = "El cliente es obligatorio")
        @Size(max = 120, message = "El cliente no puede superar 120 caracteres")
        String cliente,

        @NotBlank(message = "El email del cliente es obligatorio")
        @Email(message = "El email del cliente no tiene un formato válido")
        @Size(max = 160)
        String emailCliente,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String descripcion,

        @NotNull(message = "El total es obligatorio")
        @DecimalMin(value = "1", message = "El total debe ser mayor a 0")
        @Digits(integer = 10, fraction = 2, message = "El total admite hasta 10 enteros y 2 decimales")
        BigDecimal total,

        /* Opcional, solo para la demostración de reintentos y DLQ. */
        SimulacionFallo simularFallo) {
}
