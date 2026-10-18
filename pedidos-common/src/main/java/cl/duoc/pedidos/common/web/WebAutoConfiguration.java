package cl.duoc.pedidos.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(ManejadorErroresRest.class)
public class WebAutoConfiguration {
}
