package com.controlhorario.importexport.excel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;

/**
 * Una fila de una hoja mensual con fichaje (columna B rellena), tal como viene en el Excel.
 *
 * @param date               fecha calculada por la posición de la fila (no la de la columna A)
 * @param row                número de fila en Excel (base 1)
 * @param breaks             pausas leídas de C/D, E/F y G/H (con la regla especial de la comida ya aplicada)
 * @param remoteMinutes      O - N, solo con ubicación MIXTO
 * @param excelWorkedMinutes Total Día (columna L), para comparar con el cálculo
 * @param representable      false si la comida está escrita a mano (J) sin horas y no se puede deducir
 * @param errors             problemas que impiden importar el día (celdas ilegibles, pausas incompletas)
 * @param messages           avisos informativos (ubicación vacía, fecha corregida, regla de la comida...)
 */
public record ParsedDay(
        LocalDate date,
        String sheet,
        int row,
        LocalTime startTime,
        LocalTime endTime,
        List<BreakInput> breaks,
        Location location,
        Integer remoteMinutes,
        Integer excelWorkedMinutes,
        boolean representable,
        List<String> errors,
        List<String> messages) {

    public ParsedDay {
        breaks = List.copyOf(breaks);
        errors = List.copyOf(errors);
        messages = List.copyOf(messages);
    }
}
