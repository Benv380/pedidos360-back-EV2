package cl.duoc.pedidos.documentos.documento;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoService servicio;

    public DocumentoController(DocumentoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<Documento> listar(@RequestParam(required = false) Long pedidoId) {
        return servicio.listar(pedidoId);
    }

    @GetMapping("/{id}/contenido")
    public ResponseEntity<String> contenido(@PathVariable Long id) {
        Documento documento = servicio.obtener(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + documento.getFolio() + ".txt\"")
                .body(documento.getContenido());
    }
}
