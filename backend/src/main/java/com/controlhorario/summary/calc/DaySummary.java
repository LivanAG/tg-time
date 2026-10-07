package com.controlhorario.summary.calc;

import java.time.LocalDate;
import java.util.List;

import com.controlhorario.calendar.calc.DayType;
import com.controlhorario.common.calc.CalcIssue;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayResult;

/**
 * Una fila de la hoja mensual.
 *
 * @param dayMinutes         jornada teórica del día (0 si no es laborable)
 * @param theoreticalMinutes jornada que hay que trabajar ese día (descontadas las ausencias)
 * @param roundedMinutes     trabajado redondeado sin deriva (lo que se imputa, columna P)
 * @param countdownMinutes   teóricas del mes - trabajado acumulado hasta este día (columna T)
 * @param workday            fichaje, o null si no hay
 * @param result             totales del fichaje, o null si no hay
 */
public record DaySummary(
        LocalDate date,
        DayType dayType,
        boolean intensive,
        int dayMinutes,
        String holidayName,
        AbsenceInput absence,
        WorkdayInput workday,
        WorkdayResult result,
        int theoreticalMinutes,
        int workedMinutes,
        int roundedMinutes,
        int countdownMinutes,
        List<CalcIssue> warnings) {
}
