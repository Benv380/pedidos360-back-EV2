package cl.duoc.pedidos.pedidos.pedido;

import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import cl.duoc.pedidos.common.mensajeria.evento.TipoEvento;
import cl.duoc.pedidos.common.web.RecursoNoEncontradoException;
import cl.duoc.pedidos.common.web.ReglaNegocioException;
import cl.duoc.pedidos.pedidos.pedido.dto.CambiarEstadoRequest;
import cl.duoc.pedidos.pedidos.pedido.dto.CrearPedidoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PedidoServiceTest {

    private PedidoRepository repositorio;
    private EventosPedidoPublisher eventos;
    private PedidoService servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(PedidoRepository.class);
        eventos = mock(EventosPedidoPublisher.class);
        servicio = new PedidoService(repositorio, eventos);
        when(repositorio.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearPublicaPedidoCreadoEnFanout() {
        servicio.crear(new CrearPedidoRequest(" Ana ", "ana@correo.cl", "Notebook", new BigDecimal("500000"),
                SimulacionFallo.DOCUMENTO));

        ArgumentCaptor<PedidoEvento> evento = ArgumentCaptor.forClass(PedidoEvento.class);
        verify(eventos).publicarPedidoCreado(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.PEDIDO_CREADO);
        assertThat(evento.getValue().cliente()).isEqualTo("Ana");
        assertThat(evento.getValue().estado()).isEqualTo("CREADO");
        assertThat(evento.getValue().simularFallo()).isEqualTo(SimulacionFallo.DOCUMENTO);
    }

    @Test
    void cambiarEstadoValidoPublicaEnTopic() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(pedido()));

        Pedido actualizado = servicio.cambiarEstado(1L, new CambiarEstadoRequest(EstadoPedido.PAGADO, null));

        assertThat(actualizado.getEstado()).isEqualTo(EstadoPedido.PAGADO);
        ArgumentCaptor<PedidoEvento> evento = ArgumentCaptor.forClass(PedidoEvento.class);
        verify(eventos).publicarCambioEstado(evento.capture());
        assertThat(evento.getValue().estadoAnterior()).isEqualTo("CREADO");
        assertThat(evento.getValue().estado()).isEqualTo("PAGADO");
    }

    @Test
    void cambiarEstadoInvalidoNoPublica() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(pedido()));

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, new CambiarEstadoRequest(EstadoPedido.ENTREGADO, null)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(eventos, never()).publicarCambioEstado(any());
    }

    @Test
    void boletaSoloParaPedidosPagados() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(pedido()));

        assertThatThrownBy(() -> servicio.solicitarBoleta(1L)).isInstanceOf(ReglaNegocioException.class);
        verify(eventos, never()).solicitarBoleta(any());
    }

    @Test
    void boletaDePedidoPagadoSePublicaEnDirect() {
        Pedido pagado = pedido();
        pagado.cambiarEstado(EstadoPedido.PAGADO);
        when(repositorio.findById(1L)).thenReturn(Optional.of(pagado));

        PedidoEvento evento = servicio.solicitarBoleta(1L);

        assertThat(evento.tipo()).isEqualTo(TipoEvento.BOLETA_SOLICITADA);
        verify(eventos).solicitarBoleta(evento);
    }

    @Test
    void pedidoInexistenteLanza404() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    private static Pedido pedido() {
        return new Pedido("Ana", "ana@correo.cl", "Notebook", new BigDecimal("500000"), "ana");
    }
}
