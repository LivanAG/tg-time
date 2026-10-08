package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelCells.cell;
import static com.controlhorario.importexport.excel.ExcelReportLayout.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.controlhorario.absence.AbsenceType;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.MixedTimes;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Lee una hoja exportada por la app ({@link ExcelReportLayout}): un día por fila con su fecha en la
 * columna A, ubicación, ausencia, tramos de oficina y de casa, pausas y notas. Las filas sin fecha
 * (subtotales, total, resumen) se ignoran. No evalúa fórmulas.
 */
final class ExcelReportParser {

    /** "08:00–08:15" (guion o raya), separados por comas. */
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*[–—-]\\s*(\\d{1,2}):(\\d{2})");

    /** Hoja leída con los parámetros de su cabecera. */
    record Result(ParsedSheet sheet, DetectedSettings settings) {
    }

    private ExcelReportParser() {
    }

    static Result parse(Sheet sheet, boolean date1904, List<String> warnings) {
        String name = sheet.getSheetName();
        Map<YearMonth, Integer> monthCounts = new HashMap<>();
        List<ParsedDay> days = new ArrayList<>();
        List<ParsedAbsence> absences = new ArrayList<>();
        int rows = 0;
        for (int r = FIRST_DATA_ROW; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            Optional<LocalDate> date = date(row, date1904);
            if (date.isEmpty()) {
                if (TOTAL_LABEL.equalsIgnoreCase(textOrNull(row, COL_DATE))) {
                    break;
                }
                continue;
            }
            rows++;
            monthCounts.merge(YearMonth.from(date.get()), 1, Integer::sum);
            int excelRow = r + 1;
            readAbsence(row, name, excelRow, date.get(), warnings).ifPresent(absences::add);
            readDay(row, name, excelRow, date.get()).ifPresent(days::add);
        }
        YearMonth month = monthCounts.entrySet().stream()
                .max(Map.Entry.<YearMonth, Integer>comparingByValue()
                        .thenComparing(Map.Entry.<YearMonth, Integer>comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (month == null) {
            return null;
        }
        return new Result(new ParsedSheet(name, month, rows, 0, days, true, absences), settings(sheet));
    }

    private static Optional<ParsedAbsence> readAbsence(Row row, String sheet, int excelRow, LocalDate date,
            List<String> warnings) {
        String label = textOrNull(row, COL_ABSENCE);
        if (label == null) {
            return Optional.empty();
        }
        Optional<AbsenceType> type = absenceType(label);
        if (type.isEmpty()) {
            warnings.add(sheet + ", fila " + excelRow + ": ausencia «" + label + "» desconocida, se ignora");
            return Optional.empty();
        }
        return Optional.of(new ParsedAbsence(date, sheet, excelRow, type.get(), isHalfDay(label)));
    }

    /** Día con fichaje: la columna Ubicación dice Oficina, Casa o Mixto. Otras etiquetas (Fin de semana…) no. */
    private static Optional<ParsedDay> readDay(Row row, String sheet, int excelRow, LocalDate date) {
        String label = textOrNull(row, COL_LOCATION);
        Optional<Location> location = label == null ? Optional.empty() : location(label);
        if (location.isEmpty()) {
            return Optional.empty();
        }
        List<String> errors = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        LocalTime officeStart = time(row, COL_OFFICE_START, errors);
        LocalTime officeEnd = time(row, COL_OFFICE_END, errors);
        LocalTime homeStart = time(row, COL_HOME_START, errors);
        LocalTime homeEnd = time(row, COL_HOME_END, errors);

        LocalTime start = null;
        LocalTime end = null;
        MixedTimes mixed = null;
        switch (location.get()) {
            case OFICINA -> {
                start = officeStart;
                end = officeEnd;
                ignored(homeStart, homeEnd, "casa", messages);
            }
            case CASA -> {
                start = homeStart;
                end = homeEnd;
                ignored(officeStart, officeEnd, "oficina", messages);
            }
            case MIXTO -> {
                mixed = new MixedTimes(officeStart, officeEnd, homeStart, homeEnd);
                start = mixed.start();
                end = mixed.end();
            }
        }

        List<BreakInput> breaks = new ArrayList<>();
        addBreak(breaks, BreakType.DESAYUNO, time(row, COL_BREAKFAST_START, errors),
                time(row, COL_BREAKFAST_END, errors), "Desayuno", errors);
        addBreak(breaks, BreakType.COMIDA, time(row, COL_LUNCH_START, errors), time(row, COL_LUNCH_END, errors),
                "Comida", errors);
        readOtherBreaks(row, breaks, errors);
        breaks.sort(Comparator.comparing(BreakInput::start));

        Integer worked = duration(row, COL_WORKED, messages);
        String notes = textOrNull(row, COL_NOTES);
        return Optional.of(new ParsedDay(date, sheet, excelRow, start, end, breaks, location.get(), mixed, notes,
                worked, true, errors, messages));
    }

    private static void readOtherBreaks(Row row, List<BreakInput> breaks, List<String> errors) {
        String text = textOrNull(row, COL_OTHER_BREAKS);
        if (text == null) {
            return;
        }
        Matcher m = RANGE.matcher(text);
        int found = 0;
        while (m.find()) {
            found++;
            try {
                breaks.add(new BreakInput(BreakType.OTRA,
                        LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))),
                        LocalTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)))));
            } catch (RuntimeException e) {
                errors.add("Otras pausas: hora no válida en «" + m.group() + "»");
            }
        }
        if (found == 0) {
            errors.add("Otras pausas: no se entiende «" + text + "» (usa 11:00–11:15, separadas por comas)");
        }
    }

    private static void addBreak(List<BreakInput> breaks, BreakType type, LocalTime start, LocalTime end,
            String what, List<String> errors) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            errors.add(what + " incompleto: falta " + (start == null ? "el inicio" : "el fin"));
            return;
        }
        breaks.add(new BreakInput(type, start, end));
    }

    private static void ignored(LocalTime start, LocalTime end, String where, List<String> messages) {
        if (start != null || end != null) {
            messages.add("Hay horas en " + where + " pero la ubicación no es mixta: se ignoran");
        }
    }

    /** Parámetros de la cabecera; los que no se puedan leer quedan a null. */
    private static DetectedSettings settings(Sheet sheet) {
        Row first = sheet.getRow(PARAMS_ROW_1);
        Row second = sheet.getRow(PARAMS_ROW_2);
        return new DetectedSettings(
                safeDuration(second, COL_PARAM_1),
                safeDuration(second, COL_PARAM_2),
                safeNumber(first, COL_PARAM_3),
                safeDuration(first, COL_PARAM_1),
                safeDuration(first, COL_PARAM_2),
                null,
                null);
    }

    private static Optional<LocalDate> date(Row row, boolean date1904) {
        try {
            return ExcelCells.date(cell(row, COL_DATE), date1904);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static LocalTime time(Row row, int column, List<String> errors) {
        try {
            Integer minutes = ExcelCells.timeOfDay(cell(row, column));
            return minutes == null ? null : LocalTime.of(minutes / 60, minutes % 60);
        } catch (CellReadException e) {
            errors.add(e.getMessage());
            return null;
        }
    }

    private static Integer duration(Row row, int column, List<String> messages) {
        try {
            return ExcelCells.durationMinutes(cell(row, column));
        } catch (CellReadException e) {
            messages.add(e.getMessage() + " (Total): se ignora");
            return null;
        }
    }

    private static String textOrNull(Row row, int column) {
        try {
            return ExcelCells.text(cell(row, column));
        } catch (CellReadException e) {
            return null;
        }
    }

    private static Integer safeDuration(Row row, int column) {
        try {
            return ExcelCells.durationMinutes(cell(row, column));
        } catch (CellReadException e) {
            return null;
        }
    }

    /** Número tal cual (el porcentaje se guarda como 50 con formato "0 %"). */
    private static Integer safeNumber(Row row, int column) {
        Cell c = cell(row, column);
        return c != null && c.getCellType() == CellType.NUMERIC ? (int) Math.round(c.getNumericCellValue()) : null;
    }
}
