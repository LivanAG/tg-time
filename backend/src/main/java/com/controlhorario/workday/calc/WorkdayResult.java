package com.controlhorario.workday.calc;

import java.util.List;

import com.controlhorario.common.calc.CalcIssue;

/**
 * Totales calculados de un día. trabajado = bruto - desayunoDescontado - comidaDescontada - otras.
 *
 * @param grossMinutes             salida - entrada
 * @param breakfastMinutes         duración real del desayuno
 * @param breakfastDeductedMinutes max(0, desayuno - tolerancia) (columna K del Excel)
 * @param lunchMinutes             duración real de la comida
 * @param lunchDeductedMinutes     max(comida, comida mínima) si hubo comida (columna J)
 * @param otherBreakMinutes        suma de pausas OTRA (columnas G/H)
 * @param workedMinutes            tiempo trabajado del día (columna L)
 * @param officeMinutes            parte en la oficina
 * @param remoteMinutes            parte en casa
 */
public record WorkdayResult(
        int grossMinutes,
        int breakfastMinutes,
        int breakfastDeductedMinutes,
        int lunchMinutes,
        int lunchDeductedMinutes,
        int otherBreakMinutes,
        int workedMinutes,
        int officeMinutes,
        int remoteMinutes,
        List<CalcIssue> warnings) {
}
