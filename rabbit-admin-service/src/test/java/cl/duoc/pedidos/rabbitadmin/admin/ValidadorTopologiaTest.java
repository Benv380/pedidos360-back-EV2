package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.common.web.SolicitudInvalidaException;
import cl.duoc.pedidos.rabbitadmin.admin.dto.CrearColaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.PoliticaRequest;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoCola;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoDesborde;
import cl.duoc.pedidos.rabbitadmin.admin.dto.TipoExchange;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorTopologiaTest {

    private final ValidadorTopologia validador = new ValidadorTopologia();
    private final Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CrearColaRequest cola(String nombre) {
        return new CrearColaRequest(nombre, null, null, null, null, null, null, null, null);
    }

    @Test
    void rechazaNombreVacioEnElDto() {
        assertThat(beanValidator.validate(cola(""))).isNotEmpty();
    }

    @Test
    void rechazaNombreConCaracteresInvalidosEnElDto() {
        assertThat(beanValidator.validate(cola("mi cola!")))
                .extracting(v -> v.getPropertyPath().toString()).contains("nombre");
    }

    @Test
    void rechazaTtlFueraDeRango() {
        var solicitud = new CrearColaRequest("pagos.queue", null, null, null, null, 10L, null, null, null);
        assertThat(beanValidator.validate(solicitud)).extracting(v -> v.getPropertyPath().toString()).contains("ttlMs");
    }

    @Test
    void rechazaPrefijoReservado() {
        assertThatThrownBy(() -> validador.validarCola(cola("amq.prueba"))).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rechazaQuorumNoDurable() {
        assertThatThrownBy(() -> validador.validarCola(
                new CrearColaRequest("pagos.queue", TipoCola.QUORUM, false, null, null, null, null, null, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void desbordeRequiereLargoMaximo() {
        assertThatThrownBy(() -> validador.validarCola(
                new CrearColaRequest("demo.queue", null, null, null, true, null, null, TipoDesborde.DROP_HEAD, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void rejectPublishDlxRequiereDlq() {
        assertThatThrownBy(() -> validador.validarCola(
                new CrearColaRequest("demo.queue", null, null, null, false, null, 5, TipoDesborde.REJECT_PUBLISH_DLX, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> validador.validarCola(
                new CrearColaRequest("demo.queue", null, null, null, true, 15000L, 5, TipoDesborde.REJECT_PUBLISH_DLX, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void politicaExigeRegexValidaYAlgunLimite() {
        assertThatThrownBy(() -> validador.validarPolitica(new PoliticaRequest("^demo\\.(", null, 1000L, null, null, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> validador.validarPolitica(new PoliticaRequest("^demo\\.", null, null, null, null, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> validador.validarPolitica(new PoliticaRequest(".*", null, 1000L, null, null, null)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> validador.validarPolitica(new PoliticaRequest("^demo\\.", 1, 30000L, 100, TipoDesborde.DROP_HEAD, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void directNoAdmiteComodines() {
        assertThatThrownBy(() -> validador.validarRoutingKey(TipoExchange.DIRECT, "pedido.*"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> validador.validarRoutingKey(TipoExchange.DIRECT, "documento.boleta"))
                .doesNotThrowAnyException();
    }

    @Test
    void topicExigeComodinesComoPalabraCompleta() {
        assertThatThrownBy(() -> validador.validarRoutingKey(TipoExchange.TOPIC, "pedido.esta*"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> validador.validarRoutingKey(TipoExchange.TOPIC, "pedido..creado"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> validador.validarRoutingKey(TipoExchange.TOPIC, "pedido.estado.*"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.validarRoutingKey(TipoExchange.TOPIC, "pedido.#"))
                .doesNotThrowAnyException();
    }

    @Test
    void fanoutExigeRoutingKeyVacia() {
        assertThatThrownBy(() -> validador.validarRoutingKey(TipoExchange.FANOUT, "algo"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> validador.validarRoutingKey(TipoExchange.FANOUT, ""))
                .doesNotThrowAnyException();
    }
}
