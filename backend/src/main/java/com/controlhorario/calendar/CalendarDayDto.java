package com.controlhorario.calendar;

import java.time.LocalDate;

import com.controlhorario.absence.AbsenceDto;
import com.controlhorario.calendar.calc.DayType;

/**
 * Un día del calendario de un periodo.
 *
 * @param dayType     LABORABLE, FIN_DE_SEMANA o FESTIVO (todos los días están dentro del periodo)
 * @param intensive   el día cae en un rango de jornada intensiva
 * @param dayMinutes  jornada teórica del día (0 si no es laborable)
 * @param holidayName nombre del festivo, o null
 * @param absence     ausencia marcada ese día, o null
 * @param hasWorkday  hay fichaje ese día
 */
public record CalendarDayDto(
        LocalDate date,
        DayType dayType,
        boolean intensive,
        int dayMinutes,
        String holidayName,
        AbsenceDto absence,
        boolean hasWorkday) {
}
