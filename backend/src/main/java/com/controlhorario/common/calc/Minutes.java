package com.controlhorario.common.calc;

import java.time.LocalTime;

/** Utilidades de minutos enteros: toda la lógica trabaja en int, nunca con decimales. */
public final class Minutes {

    private Minutes() {
    }

    public static int of(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public static int between(LocalTime start, LocalTime end) {
        return of(end) - of(start);
    }

    /** 90 → "1:30", -45 → "-0:45". */
    public static String format(int minutes) {
        String sign = minutes < 0 ? "-" : "";
        int abs = Math.abs(minutes);
        return sign + (abs / 60) + ":" + String.format("%02d", abs % 60);
    }

    /** Mitad de una jornada, redondeando medio minuto hacia arriba. */
    public static int half(int minutes) {
        return (minutes + 1) / 2;
    }
}
