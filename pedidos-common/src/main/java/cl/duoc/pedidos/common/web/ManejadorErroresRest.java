package cl.duoc.pedidos.common.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respuestas de error uniformes (RFC 7807 - ProblemDetail) para todos los microservicios.
 * Los errores de validación incluyen el detalle por campo en "errores".
 */
@RestControllerAdvice
public class ManejadorErroresRest {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresRest.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errores.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors()
                .forEach(g -> errores.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos");
        pd.setProperty("errores", errores);
        return pd;
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    ProblemDetail validacionParametros(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetros inválidos: " + e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o contiene valores no permitidos");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException e) {
        return problema(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro '" + e.getName() + "'");
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ProblemDetail solicitudInvalida(SolicitudInvalidaException e) {
        return problema(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException e) {
        return problema(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AmqpException.class)
    ProblemDetail rabbitNoDisponible(AmqpException e) {
        log.error("Error al comunicarse con RabbitMQ", e);
        return problema(HttpStatus.BAD_GATEWAY, "No fue posible comunicarse con RabbitMQ: " + e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus estado, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setTitle(estado.getReasonPhrase());
        return pd;
    }
}
