package cl.duoc.pedidos.documentos.documento;

import cl.duoc.pedidos.common.mensajeria.consumo.ErrorNoRecuperableException;
import cl.duoc.pedidos.common.mensajeria.consumo.ErrorRecuperableException;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import cl.duoc.pedidos.common.web.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Generación de tickets y boletas. No conoce RabbitMQ. */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.of("America/Santiago"));

    private final DocumentoRepository repositorio;

    public DocumentoService(DocumentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public void generar(PedidoEvento evento) {
        TipoDocumento tipo = switch (evento.tipo()) {
            case PEDIDO_CREADO -> TipoDocumento.TICKET;
            case BOLETA_SOLICITADA -> TipoDocumento.BOLETA;
            default -> throw new ErrorNoRecuperableException("no se generan documentos para " + evento.tipo());
        };
        if (repositorio.existsByEventoId(evento.eventoId())) {
            log.info("Documento del evento {} ya generado; se ignora el duplicado", evento.eventoId());
            return;
        }
        if (evento.simula(SimulacionFallo.DOCUMENTO)) {
            // Error transitorio: el ProcesadorMensajes reintentará y, al agotar los intentos, enviará a la DLQ.
            throw new ErrorRecuperableException("SIMULADO: el almacenamiento de documentos no responde");
        }

        String folio = tipo.name().charAt(0) + "-" + evento.pedidoId() + "-" + evento.eventoId().toString().substring(0, 8);
        Documento documento = repositorio.save(new Documento(evento.eventoId(), evento.pedidoId(), tipo, folio,
                contenido(tipo, folio, evento)));
        log.info("[DOCUMENTO] {} folio={} pedido={}", tipo, documento.getFolio(), evento.pedidoId());
    }

    @Transactional(readOnly = true)
    public List<Documento> listar(Long pedidoId) {
        return pedidoId == null
                ? repositorio.findTop100ByOrderByIdDesc()
                : repositorio.findByPedidoIdOrderByIdDesc(pedidoId);
    }

    @Transactional(readOnly = true)
    public Documento obtener(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el documento " + id));
    }

    private static String contenido(TipoDocumento tipo, String folio, PedidoEvento e) {
        String linea = "=".repeat(40);
        return String.join("\n",
                linea,
                "  PEDIDOS 360 - " + (tipo == TipoDocumento.BOLETA ? "BOLETA ELECTRÓNICA" : "TICKET DE PEDIDO"),
                linea,
                "Folio     : " + folio,
                "Fecha     : " + FECHA.format(e.fecha()),
                "Pedido    : #" + e.pedidoId(),
                "Cliente   : " + e.cliente(),
                "Email     : " + e.emailCliente(),
                "Detalle   : " + e.descripcion(),
                "Estado    : " + e.estado(),
                "-".repeat(40),
                "TOTAL     : $" + e.total(),
                linea);
    }
}
