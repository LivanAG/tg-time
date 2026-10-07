package com.controlhorario.summary.calc;

import java.util.ArrayList;
import java.util.List;

/**
 * Redondeo a tramos (15 min) sin deriva: en lugar de redondear cada día por separado (MROUND del
 * Excel) o arrastrar el resto en una columna aparte, se redondea el acumulado del mes y cada día
 * recibe la diferencia: {@code redondeado_i = round(acumulado_i) - round(acumulado_{i-1})}.
 * Así la suma del mes nunca se separa del total real más de medio tramo (7 min con 15).
 */
public final class RoundingService {

    private final int step;

    public RoundingService(int step) {
        if (step <= 0) {
            throw new IllegalArgumentException("El tramo de redondeo debe ser positivo");
        }
        this.step = step;
    }

    /** Múltiplo de {@code step} más cercano; en empate, hacia arriba. */
    public int round(int minutes) {
        return Math.floorDiv(2 * minutes + step, 2 * step) * step;
    }

    /** Redondeado de cada día a partir de los minutos trabajados de cada día, en orden. */
    public List<Integer> distribute(List<Integer> dailyMinutes) {
        List<Integer> rounded = new ArrayList<>(dailyMinutes.size());
        int accumulated = 0;
        int previousRounded = 0;
        for (int minutes : dailyMinutes) {
            accumulated += minutes;
            int currentRounded = round(accumulated);
            rounded.add(currentRounded - previousRounded);
            previousRounded = currentRounded;
        }
        return rounded;
    }
}
