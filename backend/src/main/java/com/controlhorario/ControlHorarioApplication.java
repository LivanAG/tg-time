package com.controlhorario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Sin usuario en memoria: la autenticación es JWT (resource server) con usuarios en la base de datos.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ControlHorarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlHorarioApplication.class, args);
    }
}
