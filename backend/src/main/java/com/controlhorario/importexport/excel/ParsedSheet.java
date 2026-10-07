package com.controlhorario.importexport.excel;

import java.time.YearMonth;
import java.util.List;

/**
 * Hoja mensual reconocida.
 *
 * @param month           (año, mes) más frecuente entre las fechas de la columna A
 * @param rows            filas de datos cuya fecha calculada es de ese mes
 * @param dateCorrections filas cuya columna A tiene una fecha distinta de la calculada
 * @param days            filas con fichaje
 */
public record ParsedSheet(String name, YearMonth month, int rows, int dateCorrections, List<ParsedDay> days) {

    public ParsedSheet {
        days = List.copyOf(days);
    }
}
