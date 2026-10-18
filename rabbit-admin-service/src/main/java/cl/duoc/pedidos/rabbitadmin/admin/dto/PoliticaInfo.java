package cl.duoc.pedidos.rabbitadmin.admin.dto;

import java.util.Map;

public record PoliticaInfo(String nombre, String patron, String aplicaA, int prioridad, Map<String, Object> definicion) {
}
