package com.controlhorario.workday;

/** Totales calculados de un día (nunca se guardan). */
public record WorkdayTotalsDto(
        int grossMinutes,
        int breakfastMinutes,
        int breakfastDeductedMinutes,
        int lunchMinutes,
        int lunchDeductedMinutes,
        int otherBreakMinutes,
        int workedMinutes,
        int officeMinutes,
        int remoteMinutes) {
}
