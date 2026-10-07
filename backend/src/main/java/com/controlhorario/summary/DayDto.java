package com.controlhorario.summary;

import java.time.LocalDate;
import java.util.List;

import com.controlhorario.absence.AbsenceDto;
import com.controlhorario.calendar.calc.DayType;
import com.controlhorario.workday.IssueDto;
import com.controlhorario.workday.WorkdayDto;

/**
 * Una fila de la hoja mensual.
 *
 * @param roundedMinutes   redondeo sin deriva (columna P)
 * @param countdownMinutes teóricas del mes - trabajado acumulado hasta este día (columna T)
 */
public record DayDto(
        LocalDate date,
        DayType dayType,
        boolean intensive,
        int dayMinutes,
        String holidayName,
        AbsenceDto absence,
        WorkdayDto workday,
        int theoreticalMinutes,
        int workedMinutes,
        int roundedMinutes,
        int countdownMinutes,
        List<IssueDto> warnings) {
}
