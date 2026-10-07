package com.controlhorario.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de la aplicación (prefijo {@code app}).
 *
 * @param jwtSecret secreto HS256 para firmar los access tokens (fase 2)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String jwtSecret) {
}
