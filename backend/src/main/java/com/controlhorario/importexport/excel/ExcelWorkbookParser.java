package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelCells.cell;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.controlhorario.common.calc.Minutes;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.MixedTimes;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Lee un Excel HORAS_IZERTIS (docs/EXCEL.md) sin consultar la base de datos ni evaluar fórmulas.
 * <ul>
 *   <li>Hojas mensuales: las que tienen fechas en la columna A de las filas de datos. El mes es el
 *       (año, mes) más frecuente entre esas fechas; no se usa el nombre de la hoja ni B3.</li>
 *   <li>La fecha de cada fila se calcula por su posición en la rejilla ({@link ExcelLayout}); si la
 *       columna A dice otra cosa se cuenta como corrección.</li>
 *   <li>Regla especial: comida escrita a mano en J sin horas en E/F. Si el tramo C/D empieza a partir
 *       de las 14:00 era la comida; si no, el día no se puede representar.</li>
 * </ul>
 */
public final class ExcelWorkbookParser {

    /** Con la comida escrita a mano sin horas, un tramo C/D que empieza a partir de esta hora era la comida. */
    public static final LocalTime LUNCH_FROM = LocalTime.of(14, 0);
    /** Nombre de la hoja de resumen anual. */
    public static final String SUMMARY_SHEET = "Horas";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cabecera de una hoja mensual (F1-F3, J1/J2, L1/L2). */
    private record Header(Integer minLunchMin, Integer breakfastToleranceMin, Integer maxRemotePct,
            Integer normalDays, Integer normalDayMinutes, Integer intensiveDays, Integer intensiveDayMinutes) {
    }

    private record SheetData(ParsedSheet sheet, Header header) {
    }

    public ParsedWorkbook parse(Workbook workbook) {
        boolean date1904 = workbook instanceof XSSFWorkbook xssf && xssf.isDate1904();
        List<SheetData> monthly = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Sheet summary = null;
        for (Sheet sheet : workbook) {
            Optional<YearMonth> month = detectMonth(sheet, date1904);
            if (month.isPresent()) {
                monthly.add(parseSheet(sheet, month.get(), date1904, warnings));
            } else if (summary == null && SUMMARY_SHEET.equalsIgnoreCase(sheet.getSheetName().trim())) {
                summary = sheet;
            }
        }
        return new ParsedWorkbook(monthly.stream().map(SheetData::sheet).toList(), detectSettings(summary, monthly),
                warnings);
    }

    /** Mes de la hoja: el (año, mes) más frecuente en la columna A de las filas de datos (empate: el primero). */
    static Optional<YearMonth> detectMonth(Sheet sheet, boolean date1904) {
        Map<YearMonth, Integer> counts = new HashMap<>();
        for (int r : ExcelLayout.dataRows()) {
            safeDate(sheet.getRow(r - 1), date1904).ifPresent(d -> counts.merge(YearMonth.from(d), 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<YearMonth, Integer>comparingByValue()
                        .thenComparing(Map.Entry.<YearMonth, Integer>comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey);
    }

    private SheetData parseSheet(Sheet sheet, YearMonth month, boolean date1904, List<String> warnings) {
        String name = sheet.getSheetName();
        int rows = 0;
        List<Integer> correctedRows = new ArrayList<>();
        List<ParsedDay> days = new ArrayList<>();
        for (int r : ExcelLayout.dataRows()) {
            Row row = sheet.getRow(r - 1);
            LocalDate date = ExcelLayout.dateOf(month, r);
            if (!YearMonth.from(date).equals(month)) {
                continue;
            }
            rows++;
            LocalDate written = safeDate(row, date1904).orElse(null);
            boolean corrected = written != null && !written.equals(date);
            if (corrected) {
                correctedRows.add(r);
            }
            if (!ExcelCells.isBlank(cell(row, ExcelLayout.COL_START))) {
                days.add(parseDay(row, name, r, date, corrected ? written : null));
            }
        }
        if (!correctedRows.isEmpty()) {
            int n = correctedRows.size();
            warnings.add(name + ": " + n + (n == 1 ? " fila tiene" : " filas tienen") + " otra fecha en la columna A ("
                    + (n == 1 ? "fila " : "filas ") + ranges(correctedRows)
                    + "); se usa la fecha que corresponde a su posición en la hoja");
        }
        return new SheetData(new ParsedSheet(name, month, rows, correctedRows.size(), days), header(sheet));
    }

    private ParsedDay parseDay(Row row, String sheet, int r, LocalDate date, LocalDate written) {
        List<String> errors = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        if (written != null) {
            messages.add("La columna A dice " + DATE.format(written) + ": se usa el " + DATE.format(date)
                    + " por la posición de la fila");
        }
        LocalTime start = time(row, ExcelLayout.COL_START, errors);
        LocalTime end = time(row, ExcelLayout.COL_END, errors);
        LocalTime breakfastStart = time(row, ExcelLayout.COL_BREAKFAST_START, errors);
        LocalTime breakfastEnd = time(row, ExcelLayout.COL_BREAKFAST_END, errors);
        LocalTime lunchStart = time(row, ExcelLayout.COL_LUNCH_START, errors);
        LocalTime lunchEnd = time(row, ExcelLayout.COL_LUNCH_END, errors);
        LocalTime otherStart = time(row, ExcelLayout.COL_OTHER_START, errors);
        LocalTime otherEnd = time(row, ExcelLayout.COL_OTHER_END, errors);

        Integer lunchTotal = duration(row, ExcelLayout.COL_LUNCH_TOTAL, "total de comida", messages);
        boolean lunchWithoutTimes = lunchTotal != null && lunchTotal > 0 && lunchStart == null && lunchEnd == null;
        boolean breakfastIsLunch = lunchWithoutTimes && breakfastStart != null && !breakfastStart.isBefore(LUNCH_FROM);
        boolean representable = !lunchWithoutTimes || breakfastIsLunch;

        List<BreakInput> breaks = new ArrayList<>();
        addBreak(breaks, breakfastIsLunch ? BreakType.COMIDA : BreakType.DESAYUNO, breakfastStart, breakfastEnd,
                "C/D", errors);
        addBreak(breaks, BreakType.COMIDA, lunchStart, lunchEnd, "E/F", errors);
        addBreak(breaks, BreakType.OTRA, otherStart, otherEnd, "G/H", errors);
        breaks.sort(Comparator.comparing(BreakInput::start));
        if (breakfastIsLunch) {
            messages.add("La comida (J) no tiene horas: el tramo C/D, a partir de las 14:00, se importa como COMIDA");
        } else if (!representable) {
            messages.add("La comida (J = " + Minutes.format(lunchTotal)
                    + ") está escrita a mano sin horas: no se puede representar con pausas reales");
        }

        Location location = location(row, messages);
        List<String> remoteProblems = location == Location.MIXTO ? errors : messages;
        LocalTime remoteStart = time(row, ExcelLayout.COL_REMOTE_START, remoteProblems);
        LocalTime remoteEnd = time(row, ExcelLayout.COL_REMOTE_END, remoteProblems);
        MixedTimes mixed = null;
        if (location == Location.MIXTO) {
            if (remoteStart == null || remoteEnd == null) {
                messages.add("Ubicación mixta sin el tramo en casa completo (N/O)");
            } else if (!remoteEnd.isAfter(remoteStart)) {
                errors.add("El tramo en casa (N/O) debe terminar después de empezar");
            } else if (start != null && end != null) {
                mixed = mixedTimes(start, end, remoteStart, remoteEnd, breaks, errors);
            }
        } else if (remoteStart != null || remoteEnd != null) {
            messages.add("Hay un tramo en casa (N/O) pero la ubicación no es mixta: se ignora");
        }

        Integer excelWorked = duration(row, ExcelLayout.COL_WORKED, "Total Día", messages);

        return new ParsedDay(date, sheet, r, start, end, breaks, location, mixed, excelWorked,
                representable, errors, messages);
    }

    /**
     * Tramos de un día mixto del Excel: la jornada es B-I y el tramo en casa N/O, que empieza a la entrada
     * o termina a la salida; la oficina es el resto. Una pausa OTRA (G/H) pegada al tramo en casa es el
     * hueco entre los dos tramos (así lo escribe la exportación) y se quita de las pausas.
     */
    static MixedTimes mixedTimes(LocalTime start, LocalTime end, LocalTime homeStart, LocalTime homeEnd,
            List<BreakInput> breaks, List<String> errors) {
        if (homeEnd.equals(end) && homeStart.isAfter(start)) {
            BreakInput gap = breaks.stream()
                    .filter(b -> b.type() == BreakType.OTRA && b.end().equals(homeStart))
                    .findFirst().orElse(null);
            if (gap != null) {
                breaks.remove(gap);
            }
            return new MixedTimes(start, gap == null ? homeStart : gap.start(), homeStart, homeEnd);
        }
        if (homeStart.equals(start) && homeEnd.isBefore(end)) {
            BreakInput gap = breaks.stream()
                    .filter(b -> b.type() == BreakType.OTRA && b.start().equals(homeEnd))
                    .findFirst().orElse(null);
            if (gap != null) {
                breaks.remove(gap);
            }
            return new MixedTimes(gap == null ? homeEnd : gap.end(), end, homeStart, homeEnd);
        }
        errors.add("El tramo en casa (N/O) debe empezar a la entrada (B) o terminar a la salida (I)");
        return null;
    }

    private static Location location(Row row, List<String> messages) {
        String value;
        try {
            value = ExcelCells.text(cell(row, ExcelLayout.COL_LOCATION));
        } catch (CellReadException e) {
            messages.add(e.getMessage() + " (ubicación): se importa como OFICINA");
            return Location.OFICINA;
        }
        if (value == null) {
            messages.add("Ubicación vacía (M): se importa como OFICINA");
            return Location.OFICINA;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "O", "OFICINA" -> Location.OFICINA;
            case "C", "CASA" -> Location.CASA;
            case "M", "MIXTO", "MIXTA" -> Location.MIXTO;
            default -> {
                messages.add("Ubicación «" + value + "» desconocida (M): se importa como OFICINA");
                yield Location.OFICINA;
            }
        };
    }

    private static void addBreak(List<BreakInput> breaks, BreakType type, LocalTime start, LocalTime end,
            String columns, List<String> errors) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null) {
            errors.add("Pausa incompleta en " + columns + ": falta " + (start == null ? "el inicio" : "el fin"));
            return;
        }
        breaks.add(new BreakInput(type, start, end));
    }

    private static LocalTime time(Row row, int column, List<String> problems) {
        try {
            Integer minutes = ExcelCells.timeOfDay(cell(row, column));
            return minutes == null ? null : LocalTime.of(minutes / 60, minutes % 60);
        } catch (CellReadException e) {
            problems.add(e.getMessage());
            return null;
        }
    }

    private static Integer duration(Row row, int column, String what, List<String> messages) {
        try {
            return ExcelCells.durationMinutes(cell(row, column));
        } catch (CellReadException e) {
            messages.add(e.getMessage() + " (" + what + "): se ignora");
            return null;
        }
    }

    private static Header header(Sheet sheet) {
        return new Header(
                safe(() -> ExcelCells.durationMinutes(cell(sheet, "F1"))),
                safe(() -> ExcelCells.durationMinutes(cell(sheet, "F2"))),
                safe(() -> ExcelCells.integer(cell(sheet, "F3"))),
                safe(() -> ExcelCells.integer(cell(sheet, "J1"))),
                safe(() -> ExcelCells.durationMinutes(cell(sheet, "J2"))),
                safe(() -> ExcelCells.integer(cell(sheet, "L1"))),
                safe(() -> ExcelCells.durationMinutes(cell(sheet, "L2"))));
    }

    /**
     * Parámetros del Excel: jornada normal/intensiva de la hoja Horas (B1/C1) o, si no está, de las
     * cabeceras J2/L2; vacaciones (O3) y convenio (Q8) de la hoja Horas; el resto, el valor más
     * repetido en las cabeceras de las hojas mensuales.
     */
    private static DetectedSettings detectSettings(Sheet summary, List<SheetData> sheets) {
        Integer normal = summary == null ? null : positive(safe(() -> ExcelCells.durationMinutes(cell(summary, "B1"))));
        if (normal == null) {
            normal = mode(sheets, h -> isPositive(h.normalDays()) ? positive(h.normalDayMinutes()) : null);
        }
        Integer intensive = summary == null ? null
                : positive(safe(() -> ExcelCells.durationMinutes(cell(summary, "C1"))));
        if (intensive == null) {
            intensive = mode(sheets, h -> isPositive(h.intensiveDays()) ? positive(h.intensiveDayMinutes()) : null);
        }
        Integer vacationDays = summary == null ? null : nonNegative(safe(() -> ExcelCells.integer(cell(summary, "O3"))));
        Integer agreement = summary == null ? null : positive(safe(() -> ExcelCells.totalMinutes(cell(summary, "Q8"))));
        return new DetectedSettings(
                mode(sheets, h -> nonNegative(h.breakfastToleranceMin())),
                mode(sheets, h -> nonNegative(h.minLunchMin())),
                mode(sheets, h -> nonNegative(h.maxRemotePct())),
                normal, intensive, vacationDays, agreement);
    }

    private static Integer mode(List<SheetData> sheets, Function<Header, Integer> value) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (SheetData sheet : sheets) {
            Integer v = value.apply(sheet.header());
            if (v != null) {
                counts.merge(v, 1, Integer::sum);
            }
        }
        // En empate gana el primero que aparece.
        Map.Entry<Integer, Integer> best = null;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) {
                best = e;
            }
        }
        return best == null ? null : best.getKey();
    }

    private static Optional<LocalDate> safeDate(Row row, boolean date1904) {
        try {
            return ExcelCells.date(cell(row, ExcelLayout.COL_DATE), date1904);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Integer safe(Supplier<Integer> read) {
        try {
            return read.get();
        } catch (CellReadException e) {
            return null;
        }
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static Integer positive(Integer value) {
        return isPositive(value) ? value : null;
    }

    private static Integer nonNegative(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    /** [21, 22, 23, 24, 25, 30] → "21-25, 30". */
    static String ranges(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < values.size()) {
            int j = i;
            while (j + 1 < values.size() && values.get(j + 1) == values.get(j) + 1) {
                j++;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(values.get(i));
            if (j > i) {
                sb.append('-').append(values.get(j));
            }
            i = j + 1;
        }
        return sb.toString();
    }
}
