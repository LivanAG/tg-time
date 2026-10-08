package com.controlhorario.workday.calc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.controlhorario.workday.Location;

/**
 * Fichaje de un día tal como lo introduce el usuario (sin totales).
 *
 * @param start entrada; en MIXTO se calcula: la primera entrada de los dos tramos
 * @param end   salida; en MIXTO se calcula: la última salida de los dos tramos
 * @param mixed tramos de oficina y de casa, solo con MIXTO (null en otro caso)
 */
public record WorkdayInput(
        LocalDate date,
        LocalTime start,
        LocalTime end,
        List<BreakInput> breaks,
        Location location,
        MixedTimes mixed) {

    public WorkdayInput {
        breaks = breaks == null ? List.of() : List.copyOf(breaks);
        location = location == null ? Location.OFICINA : location;
        if (location != Location.MIXTO) {
            mixed = null;
        } else if (mixed != null && mixed.complete()) {
            start = mixed.start();
            end = mixed.end();
        }
    }
}
