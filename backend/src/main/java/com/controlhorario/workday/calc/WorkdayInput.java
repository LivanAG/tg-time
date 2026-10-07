package com.controlhorario.workday.calc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.controlhorario.workday.Location;

/** Fichaje de un día tal como lo introduce el usuario (sin totales). */
public record WorkdayInput(
        LocalDate date,
        LocalTime start,
        LocalTime end,
        List<BreakInput> breaks,
        Location location,
        Integer remoteMinutes) {

    public WorkdayInput {
        breaks = breaks == null ? List.of() : List.copyOf(breaks);
        location = location == null ? Location.OFICINA : location;
    }
}
