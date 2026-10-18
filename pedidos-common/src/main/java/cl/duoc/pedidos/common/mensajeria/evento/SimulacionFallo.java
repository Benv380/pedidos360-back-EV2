package cl.duoc.pedidos.common.mensajeria.evento;

/**
 * Solo para la demostración: permite forzar un error en un consumidor específico
 * y así mostrar reintentos, NACK y la llegada del mensaje a la DLQ.
 */
public enum SimulacionFallo {
    NINGUNA,
    /** notificaciones-service lanza un error NO recuperable: NACK directo a notificaciones.dlq */
    NOTIFICACION,
    /** documentos-service lanza un error RECUPERABLE: reintentos y luego NACK a documentos.dlq */
    DOCUMENTO,
    /** notificaciones-service (auditoría) lanza un error NO recuperable: NACK a auditoria.dlq */
    AUDITORIA
}
