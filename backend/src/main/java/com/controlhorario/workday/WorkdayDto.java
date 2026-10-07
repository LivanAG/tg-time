package com.controlhorario.workday;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Fichaje de un día con sus totales y avisos calculados.
 *
 * @param remoteMinutes minutos en casa indicados (solo con MIXTO; null en otro caso)
 * @param version       versión para el bloqueo optimista
 */
public record WorkdayDto(
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        List<BreakDto> breaks,
        Location location,
        Integer remoteMinutes,
        String notes,
        Long version,
        WorkdayTotalsDto totals,
        List<IssueDto> warnings) {
}
