package com.controlhorario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Sin usuario en memoria por defecto: la autenticación JWT llega en la fase 2.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ControlHorarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControlHorarioApplication.class, args);
    }
}
