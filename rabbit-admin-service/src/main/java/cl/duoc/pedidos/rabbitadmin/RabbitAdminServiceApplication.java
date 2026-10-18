package cl.duoc.pedidos.rabbitadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RabbitAdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RabbitAdminServiceApplication.class, args);
    }
}
