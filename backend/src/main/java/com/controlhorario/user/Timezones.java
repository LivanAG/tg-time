package com.controlhorario.user;

import java.time.ZoneId;
import java.util.Set;

/** Zonas horarias IANA aceptadas (las que conoce {@link ZoneId}, p. ej. "Europe/Madrid"). */
public final class Timezones {

    public static final String DEFAULT = "Europe/Madrid";

    private static final Set<String> AVAILABLE = Set.copyOf(ZoneId.getAvailableZoneIds());

    private Timezones() {
    }

    public static boolean isValid(String timezone) {
        return timezone != null && AVAILABLE.contains(timezone);
    }
}
