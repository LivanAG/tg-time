package com.controlhorario.workday;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Fichaje de un día con sus totales y avisos calculados.
 *
 * @param startTime   entrada (en MIXTO, la primera de los dos tramos)
 * @param endTime     salida (en MIXTO, la última de los dos tramos)
 * @param officeStart solo con MIXTO (null en otro caso): tramo de oficina y de casa
 * @param version     versión para el bloqueo optimista
 */
public record WorkdayDto(
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<BreakDto> breaks,
        Location location,
        @JsonFormat(pattern = "HH:mm") LocalTime officeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime officeEnd,
        @JsonFormat(pattern = "HH:mm") LocalTime homeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime homeEnd,
        String notes,
        Long version,
        WorkdayTotalsDto totals,
        List<IssueDto> warnings) {
}
