package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.rabbitadmin.admin.dto.Alerta;
import cl.duoc.pedidos.rabbitadmin.admin.dto.ColaInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.NodoInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.RetencionInfo;
import cl.duoc.pedidos.rabbitadmin.admin.dto.SaludMensajeria;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnalizadorSaludTest {

    private final AnalizadorSalud analizador = new AnalizadorSalud();
    private static final RetencionInfo RETENCION = new RetencionInfo(86_400_000L, 10_000L, "reject-publish-dlx", null, "argumentos de la cola");
    private static final NodoInfo NODO_OK = new NodoInfo("rabbit@rabbitmq1", true, "disc", 1, 2, 1000, false, false);

    @Test
    void sistemaSanoNoGeneraAlertas() {
        SaludMensajeria salud = analizar(List.of(cola("documentos.queue", 0, 0, 1, 0.0, 0.0, RETENCION)), NODO_OK);
        assertThat(salud.alertas()).singleElement().extracting(Alerta::nivel).isEqualTo(Alerta.Nivel.INFO);
    }

    @Test
    void colaSinConsumidoresConPendientesEsCritica() {
        SaludMensajeria salud = analizar(List.of(cola("documentos.queue", 120, 0, 0, 0.0, 0.0, RETENCION)), NODO_OK);
        assertThat(salud.alertas()).anySatisfy(a -> {
            assertThat(a.nivel()).isEqualTo(Alerta.Nivel.CRITICO);
            assertThat(a.mensaje()).contains("Sin consumidores");
        });
        assertThat(salud.mensajesListos()).isEqualTo(120);
    }

    @Test
    void ocupacionAltaExplicaElDesborde() {
        RetencionInfo limite5 = new RetencionInfo(15_000L, 5L, "reject-publish-dlx", null, "argumentos de la cola");
        SaludMensajeria salud = analizar(List.of(cola("demo.queue", 5, 0, 1, 0.0, 0.0, limite5)), NODO_OK);
        assertThat(salud.alertas()).anySatisfy(a -> {
            assertThat(a.nivel()).isEqualTo(Alerta.Nivel.CRITICO);
            assertThat(a.impacto()).contains("DLQ");
        });
    }

    @Test
    void mensajesEnDlqSeReportanYSeSuman() {
        ColaInfo dlq = new ColaInfo("documentos.dlq", "classic", true, false, "n1", "running", 3, 3, 0, 0, null,
                0, 0, 0, 0, true, true, null, RETENCION, 0.1, Map.of());
        SaludMensajeria salud = analizar(List.of(dlq), NODO_OK);
        assertThat(salud.mensajesEnDlq()).isEqualTo(3);
        assertThat(salud.alertas()).anySatisfy(a -> assertThat(a.nivel()).isEqualTo(Alerta.Nivel.ADVERTENCIA));
    }

    @Test
    void produccionMayorQueConsumoAdvierteEscalar() {
        SaludMensajeria salud = analizar(List.of(cola("documentos.queue", 500, 10, 1, 50.0, 5.0, RETENCION)), NODO_OK);
        assertThat(salud.alertas()).anySatisfy(a -> assertThat(a.impacto()).contains("max-concurrency"));
    }

    @Test
    void alarmaDeMemoriaBloqueaProductores() {
        NodoInfo alarma = new NodoInfo("rabbit@rabbitmq2", true, "disc", 9, 10, 1000, true, false);
        SaludMensajeria salud = analizar(List.of(), alarma);
        assertThat(salud.alertas().getFirst().nivel()).isEqualTo(Alerta.Nivel.CRITICO);
        assertThat(salud.alertas().getFirst().impacto()).contains("BLOQUEA");
    }

    @Test
    void colaSinRetencionSeInforma() {
        RetencionInfo sinLimites = new RetencionInfo(null, null, null, null, "sin límites");
        SaludMensajeria salud = analizar(List.of(cola("libre.queue", 0, 0, 1, 0.0, 0.0, sinLimites)), NODO_OK);
        assertThat(salud.alertas()).anySatisfy(a -> assertThat(a.mensaje()).contains("Sin políticas de retención"));
    }

    @Test
    void retencionEfectivaTomaElMenorEntreArgumentoYPolitica() {
        RetencionInfo r = RabbitAdminService.retencion(Map.of("x-max-length", 10_000, "x-message-ttl", 60_000),
                Map.of("max-length", 100, "message-ttl", 120_000, "expires", 300_000), "limite-demo");
        assertThat(r.maxMensajes()).isEqualTo(100);
        assertThat(r.ttlMs()).isEqualTo(60_000);
        assertThat(r.expiraMs()).isEqualTo(300_000);
        assertThat(r.origen()).contains("argumentos + política limite-demo");
    }

    private SaludMensajeria analizar(List<ColaInfo> colas, NodoInfo nodo) {
        return analizador.analizar(colas, List.of(nodo), 0, 0, 0, 10);
    }

    private static ColaInfo cola(String nombre, long listos, long sinAck, int consumidores, double pub, double entrega,
                                 RetencionInfo retencion) {
        long total = listos + sinAck;
        Double ocupacion = retencion.maxMensajes() != null ? total * 100.0 / retencion.maxMensajes() : null;
        return new ColaInfo(nombre, "classic", true, false, "rabbit@rabbitmq1", "running", total, listos, sinAck,
                consumidores, null, pub, entrega, 0, 0, false, false, null, retencion, ocupacion, Map.of());
    }
}
