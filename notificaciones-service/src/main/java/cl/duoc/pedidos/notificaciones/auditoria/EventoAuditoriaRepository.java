package cl.duoc.pedidos.notificaciones.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<EventoAuditoria> findTop200ByOrderByIdDesc();
}
