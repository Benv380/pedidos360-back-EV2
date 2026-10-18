package cl.duoc.pedidos.rabbitadmin.admin.dto;

public record ExchangeInfo(String nombre, String tipo, boolean durable, boolean autoDelete, boolean protegido) {
}
