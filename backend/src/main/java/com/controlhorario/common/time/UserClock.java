package com.controlhorario.common.time;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.controlhorario.user.UserRepository;

import org.springframework.stereotype.Component;

/** "Hoy" en la zona horaria del usuario (Europe/Madrid por defecto). */
@Component
public class UserClock {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Madrid");

    private final Clock clock;
    private final UserRepository users;

    public UserClock(Clock clock, UserRepository users) {
        this.clock = clock;
        this.users = users;
    }

    public LocalDate today(UUID userId) {
        return LocalDate.now(clock.withZone(zoneOf(userId)));
    }

    public ZoneId zoneOf(UUID userId) {
        return users.findById(userId)
                .map(u -> {
                    try {
                        return ZoneId.of(u.getTimezone());
                    } catch (DateTimeException e) {
                        return DEFAULT_ZONE;
                    }
                })
                .orElse(DEFAULT_ZONE);
    }
}
