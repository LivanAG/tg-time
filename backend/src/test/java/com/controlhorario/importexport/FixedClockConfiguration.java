package com.controlhorario.importexport;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** "Hoy" = 07/10/2026 a las 10:00 en Madrid (el día de referencia del Excel). */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, MADRID).toInstant(), MADRID);
    }
}
