package cl.duoc.pedidos.rabbitadmin.admin;

import cl.duoc.pedidos.common.web.SolicitudInvalidaException;
import org.springframework.amqp.AmqpException;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lectura del estado del broker mediante la API HTTP de RabbitMQ Management.
 * Solo lo usa {@link RabbitAdminService}; devuelve el JSON crudo de RabbitMQ.
 */
@Component
class RabbitManagementClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Map<String, Object>> OBJETO =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final String vhost;

    RabbitManagementClient(RestClient rabbitManagementRestClient, RabbitProperties rabbit) {
        this.restClient = rabbitManagementRestClient;
        this.vhost = rabbit.getVirtualHost() != null ? rabbit.getVirtualHost() : "/";
    }

    List<Map<String, Object>> colas() {
        return listar("/api/queues/{vhost}");
    }

    List<Map<String, Object>> exchanges() {
        return listar("/api/exchanges/{vhost}");
    }

    List<Map<String, Object>> bindings() {
        return listar("/api/bindings/{vhost}");
    }

    List<Map<String, Object>> nodos() {
        try {
            return restClient.get().uri("/api/nodes").retrieve().body(LISTA);
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    /** Totales y tasas globales del broker (message_stats, queue_totals, object_totals). */
    Map<String, Object> overview() {
        try {
            Map<String, Object> resultado = restClient.get().uri("/api/overview").retrieve().body(OBJETO);
            return resultado != null ? resultado : Map.of();
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    List<Map<String, Object>> politicas() {
        return listar("/api/policies/{vhost}");
    }

    Optional<Map<String, Object>> politica(String nombre) {
        return obtener("/api/policies/{vhost}/{nombre}", nombre);
    }

    /** Crea o reemplaza una política (PUT idempotente). */
    void guardarPolitica(String nombre, Map<String, Object> cuerpo) {
        try {
            restClient.put().uri("/api/policies/{vhost}/{nombre}", vhost, nombre)
                    .contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest e) {
            throw new SolicitudInvalidaException("RabbitMQ rechazó la política: " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    void eliminarPolitica(String nombre) {
        try {
            restClient.delete().uri("/api/policies/{vhost}/{nombre}", vhost, nombre).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    Optional<Map<String, Object>> exchange(String nombre) {
        return obtener("/api/exchanges/{vhost}/{nombre}", nombre);
    }

    Optional<Map<String, Object>> cola(String nombre) {
        return obtener("/api/queues/{vhost}/{nombre}", nombre);
    }

    private List<Map<String, Object>> listar(String uri) {
        try {
            List<Map<String, Object>> resultado = restClient.get().uri(uri, vhost).retrieve().body(LISTA);
            return resultado != null ? resultado : List.of();
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    private Optional<Map<String, Object>> obtener(String uri, String nombre) {
        try {
            return Optional.ofNullable(restClient.get().uri(uri, vhost, nombre).retrieve().body(OBJETO));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw noDisponible(e);
        }
    }

    private static AmqpException noDisponible(RestClientException e) {
        return new AmqpException("la API de RabbitMQ Management no respondió (" + e.getMessage() + ")", e);
    }
}
