package com.controlhorario.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tareas periódicas de autenticación: limpieza de refresh tokens caducados y del rate limit. */
@Configuration
@EnableScheduling
class AuthSchedulingConfig {
}
