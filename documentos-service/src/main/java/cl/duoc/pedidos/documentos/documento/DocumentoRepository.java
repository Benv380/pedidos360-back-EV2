package cl.duoc.pedidos.documentos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    boolean existsByEventoId(UUID eventoId);

    List<Documento> findTop100ByOrderByIdDesc();

    List<Documento> findByPedidoIdOrderByIdDesc(Long pedidoId);
}
