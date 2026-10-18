package cl.duoc.pedidos.rabbitadmin.admin.dto;

public record BindingInfo(String exchange, String destino, String tipoDestino, String routingKey) {
}
