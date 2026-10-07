package com.controlhorario.importexport.dto;

import java.time.YearMonth;

/**
 * Hoja mensual reconocida.
 *
 * @param rows            filas de datos que son días de ese mes
 * @param dateCorrections filas cuya columna A tenía una fecha distinta de la de su posición
 */
public record ImportSheetDto(String name, YearMonth month, int rows, int dateCorrections) {
}
