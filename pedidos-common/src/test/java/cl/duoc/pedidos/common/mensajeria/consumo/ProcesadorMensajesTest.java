package cl.duoc.pedidos.common.mensajeria.consumo;

import cl.duoc.pedidos.common.mensajeria.config.MensajeriaProperties;
import cl.duoc.pedidos.common.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidos.common.mensajeria.evento.SimulacionFallo;
import cl.duoc.pedidos.common.mensajeria.evento.TipoEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcesadorMensajesTest {

    private static final long TAG = 7L;
    private static final String COLA = "documentos.queue";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RabbitTemplate rabbitTemplate;
    private Channel canal;
    private ProcesadorMensajes procesador;

    @BeforeEach
    void preparar() {
        rabbitTemplate = mock(RabbitTemplate.class);
        canal = mock(Channel.class);
        procesador = new ProcesadorMensajes(objectMapper, rabbitTemplate, new MensajeriaProperties.Reintentos(3, 0));
    }

    @Test
    void confirmaConAckCuandoLaLogicaTerminaBien() throws Exception {
        procesador.procesar(mensaje(evento(), 0), canal, PedidoEvento.class, e -> { });

        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void rechazaHaciaDlqSiElErrorNoEsRecuperable() throws Exception {
        procesador.procesar(mensaje(evento(), 0), canal, PedidoEvento.class, e -> {
            throw new ErrorNoRecuperableException("email inválido");
        });

        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(anyLong(), any(Boolean.class));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void reintentaRepublicandoConContadorSiElErrorEsRecuperable() throws Exception {
        procesador.procesar(mensaje(evento(), 1), canal, PedidoEvento.class, e -> {
            throw new ErrorRecuperableException("servicio caído");
        });

        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(COLA), copia.capture());
        assertThat((Object) copia.getValue().getMessageProperties().getHeader(ProcesadorMensajes.CABECERA_REINTENTOS))
                .isEqualTo(2);
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void envíaADlqCuandoSeAgotanLosReintentos() throws Exception {
        procesador.procesar(mensaje(evento(), 3), canal, PedidoEvento.class, e -> {
            throw new ErrorRecuperableException("servicio caído");
        });

        verify(canal).basicNack(TAG, false, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void rechazaHaciaDlqSiElJsonEsInvalido() throws Exception {
        Message invalido = new Message("{no-es-json".getBytes(StandardCharsets.UTF_8), propiedades(0));

        procesador.procesar(invalido, canal, PedidoEvento.class, e -> { });

        verify(canal).basicNack(TAG, false, false);
    }

    private Message mensaje(PedidoEvento evento, int reintentosPrevios) throws Exception {
        return new Message(objectMapper.writeValueAsBytes(evento), propiedades(reintentosPrevios));
    }

    private static MessageProperties propiedades(int reintentosPrevios) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setConsumerQueue(COLA);
        props.setMessageId("m-1");
        if (reintentosPrevios > 0) {
            props.setHeader(ProcesadorMensajes.CABECERA_REINTENTOS, reintentosPrevios);
        }
        return props;
    }

    private static PedidoEvento evento() {
        return new PedidoEvento(UUID.randomUUID(), TipoEvento.PEDIDO_CREADO, 1L, "Ana", "ana@correo.cl", "Notebook",
                new BigDecimal("1000"), "CREADO", null, "ana", SimulacionFallo.NINGUNA, Instant.now());
    }
}
