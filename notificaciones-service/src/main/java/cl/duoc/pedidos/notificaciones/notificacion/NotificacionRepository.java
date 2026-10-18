package cl.duoc.pedidos.notificaciones.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<Notificacion> findTop100ByOrderByIdDesc();
}
