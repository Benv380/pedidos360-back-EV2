package cl.duoc.pedidos.rabbitadmin.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Publica mensajes sintéticos en una cola para demostrar límites, TTL y desborde. */
public record PublicarPruebaRequest(
        @Min(value = 1, message = "Debe publicar al menos 1 mensaje")
        @Max(value = 1000, message = "Máximo 1000 mensajes por solicitud")
        int cantidad,

        @Size(max = 500, message = "El contenido no puede superar 500 caracteres")
        String contenido) {
}
