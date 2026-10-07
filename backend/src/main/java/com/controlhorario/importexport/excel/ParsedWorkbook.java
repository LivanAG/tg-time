package com.controlhorario.importexport.excel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.TreeSet;

import com.controlhorario.calendar.calc.PeriodCalendar;

/**
 * Resultado de leer el Excel, sin consultar la base de datos.
 *
 * @param sheets                    hojas mensuales en el orden del fichero
 * @param warnings avisos generales (fechas corregidas...)
 */
public record ParsedWorkbook(
        List<ParsedSheet> sheets,
        DetectedSettings settings,
        List<String> warnings) {

    public ParsedWorkbook {
        sheets = List.copyOf(sheets);
        warnings = List.copyOf(warnings);
    }

    public List<ParsedDay> days() {
        return sheets.stream().flatMap(s -> s.days().stream()).toList();
    }

    /** Meses que cubre el fichero, sin repetir y en orden. */
    public List<YearMonth> months() {
        return List.copyOf(new TreeSet<>(sheets.stream().map(ParsedSheet::month).toList()));
    }

    /** Todos los días naturales de los meses que cubre el fichero. */
    public List<LocalDate> monthDates() {
        return months().stream().flatMap(m -> PeriodCalendar.datesOf(m).stream()).toList();
    }
}
