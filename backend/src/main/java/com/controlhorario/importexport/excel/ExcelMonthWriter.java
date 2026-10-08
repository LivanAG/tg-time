package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelReportLayout.*;
import static com.controlhorario.importexport.excel.ExcelSheets.*;
import static com.controlhorario.importexport.excel.ExcelStyles.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.controlhorario.calendar.calc.DayType;
import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.summary.calc.DaySummary;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.summary.calc.WeekSummary;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.MixedTimes;
import com.controlhorario.workday.calc.WorkdayInput;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Hoja mensual con el diseño de la app ({@link ExcelReportLayout}, docs/EXCEL.md): pensada para leerse e
 * imprimirse y que {@link ExcelReportParser} pueda volver a importar. Todos los días del mes (fines de
 * semana, festivos y ausencias rotulados), los tramos de oficina y de casa en sus columnas, el total y el
 * redondeado juntos, subtotales por semana, total del mes y resumen. Valores calculados, sin fórmulas.
 */
public final class ExcelMonthWriter {

    /**
     * Datos de un mes.
     *
     * @param periodName nombre y fechas del periodo, p. ej. "2026-2027 (26/05/2026 – 25/05/2027)"
     * @param summary    resumen del mes (MonthSummaryService): días, totales, subtotales y saldos
     * @param notes      notas de cada día con fichaje
     */
    public record MonthData(String userName, String company, String periodName, PeriodRules rules,
            MonthSummary summary, Map<LocalDate, String> notes) {
    }

    /** Anchos de columna A-P, en caracteres. */
    private static final int[] WIDTHS = {12, 11, 11, 27, 9, 9, 9, 9, 9, 9, 9, 9, 24, 9, 12, 40};
    private static final float ROW_HEIGHT = 17;
    private static final float LINE_HEIGHT = 13;

    /** Libro con la hoja del mes. */
    public byte[] write(MonthData data) {
        return workbook((workbook, styles) -> addSheet(workbook, styles, data));
    }

    /** Añade la hoja del mes ({@link #sheetName}) a un libro. */
    void addSheet(XSSFWorkbook workbook, ExcelStyles styles, MonthData data) {
        YearMonth month = data.summary().month();
        Sheet sheet = workbook.createSheet(sheetName(month));
        writeHeader(sheet, styles, "Registro de jornada · " + capitalize(month.getMonth()
                .getDisplayName(TextStyle.FULL, SPANISH)) + " de " + month.getYear(), data.userName(), data.company(),
                data.periodName(), data.rules());
        writeTableHeader(sheet, styles);
        int next = writeDays(sheet, styles, data);
        next = writeTotal(sheet, styles, data.summary(), next);
        writeSummary(sheet, styles, data.summary(), data.rules(), next + 1);
        setColumnWidths(sheet, WIDTHS);
        // Solo la cabecera queda fija (sin línea vertical entre columnas); al imprimir, el resumen en otra página.
        sheet.createFreezePane(0, FIRST_DATA_ROW);
        sheet.setRowBreak(next - 1);
        setUpPrint(sheet, (short) 0);
    }

    /** "Junio 2026". */
    public static String sheetName(YearMonth month) {
        return monthName(month);
    }

    // ------------------------------------------------------------------ cabecera de la tabla

    private static void writeTableHeader(Sheet sheet, ExcelStyles styles) {
        Row group = row(sheet, GROUP_HEADER_ROW);
        Row sub = row(sheet, HEADER_ROW);
        group.setHeightInPoints(20);
        sub.setHeightInPoints(18);
        for (int c = 0; c <= LAST_COL; c++) {
            text(group, c, null, styles.header());
            text(sub, c, null, styles.header());
        }
        String[][] single = {
            {"0", HEADER_DATE}, {"1", "Día"}, {"2", "Ubicación"}, {"3", "Ausencia"},
            {"12", "Otras pausas"}, {"13", "Total"}, {"14", "Redondeado"}, {"15", "Notas"}};
        for (String[] h : single) {
            int c = Integer.parseInt(h[0]);
            text(group, c, h[1], styles.header());
            sheet.addMergedRegion(new CellRangeAddress(GROUP_HEADER_ROW, HEADER_ROW, c, c));
        }
        String[][] pairs = {
            {"4", HEADER_OFFICE, "Entrada", "Salida"}, {"6", HEADER_HOME, "Entrada", "Salida"},
            {"8", "Desayuno", "Inicio", "Fin"}, {"10", "Comida", "Inicio", "Fin"}};
        for (String[] h : pairs) {
            int c = Integer.parseInt(h[0]);
            text(group, c, h[1], styles.header());
            sheet.addMergedRegion(new CellRangeAddress(GROUP_HEADER_ROW, GROUP_HEADER_ROW, c, c + 1));
            text(sub, c, h[2], styles.header());
            text(sub, c + 1, h[3], styles.header());
        }
    }

    // ------------------------------------------------------------------ días

    /** Escribe los días y los subtotales semanales; devuelve la siguiente fila libre. */
    private static int writeDays(Sheet sheet, ExcelStyles styles, MonthData data) {
        MonthSummary summary = data.summary();
        Map<LocalDate, WeekSummary> weekByEnd = summary.weeks().stream()
                .collect(Collectors.toMap(WeekSummary::weekEnd, w -> w));
        int r = FIRST_DATA_ROW;
        for (DaySummary day : summary.days()) {
            writeDay(row(sheet, r++), styles, day, data.notes().get(day.date()));
            WeekSummary week = weekByEnd.get(day.date());
            if (week != null) {
                writeWeek(sheet, row(sheet, r), styles, week);
                r++;
            }
        }
        return r;
    }

    private static void writeDay(Row row, ExcelStyles styles, DaySummary day, String notes) {
        row.setHeightInPoints(ROW_HEIGHT);
        WorkdayInput workday = day.workday();
        Fill fill = fillOf(day);
        for (int c = 0; c <= LAST_COL; c++) {
            text(row, c, null, styles.cell(kindOf(c), fill, false));
        }
        Cell date = row.getCell(COL_DATE);
        date.setCellValue(day.date());
        text(row, COL_WEEKDAY, capitalize(day.date().getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH)), null);
        if (day.absence() != null) {
            text(row, COL_ABSENCE, absenceLabel(day.absence().type(), day.absence().halfDay()), null);
        }
        if (workday == null) {
            text(row, COL_LOCATION, dayLabel(day), null);
            return;
        }
        text(row, COL_LOCATION, LOCATION_LABELS.get(workday.location()), null);
        switch (workday.location()) {
            case OFICINA -> {
                time(row, COL_OFFICE_START, workday.start());
                time(row, COL_OFFICE_END, workday.end());
            }
            case CASA -> {
                time(row, COL_HOME_START, workday.start());
                time(row, COL_HOME_END, workday.end());
            }
            case MIXTO -> {
                MixedTimes m = workday.mixed();
                time(row, COL_OFFICE_START, m.officeStart());
                time(row, COL_OFFICE_END, m.officeEnd());
                time(row, COL_HOME_START, m.homeStart());
                time(row, COL_HOME_END, m.homeEnd());
            }
        }
        writeBreak(row, workday.breaks(), BreakType.DESAYUNO, COL_BREAKFAST_START);
        writeBreak(row, workday.breaks(), BreakType.COMIDA, COL_LUNCH_START);
        String others = workday.breaks().stream()
                .filter(b -> b.type() == BreakType.OTRA)
                .map(b -> hhmm(b.start()) + "–" + hhmm(b.end()))
                .collect(Collectors.joining(", "));
        if (!others.isEmpty()) {
            text(row, COL_OTHER_BREAKS, others, null);
        }
        if (day.result() != null) {
            minutes(row, COL_WORKED, day.workedMinutes(), null);
            minutes(row, COL_ROUNDED, day.roundedMinutes(), null);
        }
        if (notes != null && !notes.isBlank()) {
            text(row, COL_NOTES, notes, null);
        }
        // Excel no ajusta solo la altura de una fila con alto fijo: se calcula para que quepan pausas y notas.
        int lines = Math.max(lines(others, COL_OTHER_BREAKS), lines(notes, COL_NOTES));
        if (lines > 1) {
            row.setHeightInPoints(LINE_HEIGHT * lines + 4);
        }
    }

    /** Líneas que ocupa un texto con ajuste en una columna (aprox.: ~1,1 caracteres por unidad de ancho). */
    static int lines(String text, int column) {
        if (text == null || text.isBlank()) {
            return 1;
        }
        int perLine = (int) (WIDTHS[column] * 1.1);
        int lines = 0;
        for (String paragraph : text.strip().split("\\R")) {
            lines++;
            int used = 0;
            for (String word : paragraph.split(" ")) {
                int length = word.length();
                if (used > 0 && used + 1 + length > perLine) {
                    lines++;
                    used = 0;
                }
                used += (used > 0 ? 1 : 0) + length;
                for (; used > perLine; used -= perLine) {
                    lines++;
                }
            }
        }
        return lines;
    }

    private static void writeWeek(Sheet sheet, Row row, ExcelStyles styles, WeekSummary week) {
        row.setHeightInPoints(ROW_HEIGHT);
        for (int c = 0; c <= LAST_COL; c++) {
            Kind kind = c == COL_WORKED || c == COL_ROUNDED ? Kind.DURATION : Kind.TEXT;
            text(row, c, null, styles.cell(kind, Fill.SUBTOTAL, true));
        }
        int difference = week.workedMinutes() - week.theoreticalMinutes();
        text(row, COL_DATE, "Semana " + shortDate(week.weekStart()) + " – " + shortDate(week.weekEnd())
                + "  ·  teóricas " + Minutes.format(week.theoreticalMinutes())
                + "  ·  diferencia " + signed(difference), null);
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), COL_DATE, COL_OTHER_BREAKS));
        minutes(row, COL_WORKED, week.workedMinutes(), null);
        minutes(row, COL_ROUNDED, week.roundedMinutes(), null);
    }

    private static int writeTotal(Sheet sheet, ExcelStyles styles, MonthSummary summary, int r) {
        Row row = row(sheet, r);
        row.setHeightInPoints(20);
        for (int c = 0; c <= LAST_COL; c++) {
            Kind kind = c == COL_WORKED || c == COL_ROUNDED ? Kind.DURATION : Kind.TEXT;
            text(row, c, null, styles.cell(kind, Fill.TOTAL, true));
        }
        text(row, COL_DATE, TOTAL_LABEL, null);
        sheet.addMergedRegion(new CellRangeAddress(r, r, COL_DATE, COL_OTHER_BREAKS));
        minutes(row, COL_WORKED, summary.workedMinutes(), null);
        minutes(row, COL_ROUNDED, summary.roundedMinutes(), null);
        return r + 1;
    }

    // ------------------------------------------------------------------ resumen

    /*
     * Cuatro bloques, como el cierre del mes de la app. A la izquierda Horas y Ausencias (etiqueta A:C,
     * valores en D y E:F); a la derecha Saldo y Teletrabajo (etiqueta I:L, valor en M).
     */
    private static final int LEFT_LABEL = COL_DATE;
    private static final int LEFT_LABEL_END = COL_LOCATION;
    private static final int LEFT_VALUE = COL_ABSENCE;
    private static final int LEFT_VALUE_2 = COL_OFFICE_START;
    private static final int LEFT_VALUE_2_END = COL_OFFICE_END;
    private static final int RIGHT_LABEL = COL_BREAKFAST_START;
    private static final int RIGHT_LABEL_END = COL_LUNCH_END;
    private static final int RIGHT_VALUE = COL_OTHER_BREAKS;

    private static void writeSummary(Sheet sheet, ExcelStyles styles, MonthSummary s, PeriodRules rules,
            int start) {
        Row title = row(sheet, start);
        title.setHeightInPoints(24);
        text(title, COL_DATE, "Resumen del mes", styles.plain(true, 13, NAVY));

        int r = start + 1;
        boxRow(sheet, styles, r, true, false, "Horas", Value.text("Mes completo"), Value.text("Hasta hoy"));
        boxRow(sheet, styles, r + 1, false, false, "Teóricas", Value.duration(s.theoreticalMinutes()),
                Value.duration(s.theoreticalToDateMinutes()));
        boxRow(sheet, styles, r + 2, false, false, "Hechas sin redondear", Value.duration(s.workedMinutes()),
                Value.duration(s.workedToDateMinutes()));
        boxRow(sheet, styles, r + 3, false, false, "Hechas redondeadas", Value.duration(s.roundedMinutes()),
                Value.duration(s.roundedToDateMinutes()));
        boxRow(sheet, styles, r + 4, false, true, "Diferencia", Value.signed(s.differenceMinutes()),
                Value.signed(s.differenceToDateMinutes()));

        boxRow(sheet, styles, r, true, false, "Saldo", Value.text(""));
        boxRow(sheet, styles, r + 1, false, false, "Saldo de apertura", Value.signed(s.openingBalanceMinutes()));
        boxRow(sheet, styles, r + 2, false, false, "+ Diferencia del mes", Value.signed(s.differenceMinutes()));
        boxRow(sheet, styles, r + 3, false, false, "− Puentes a recuperar (" + days(s.bridgeDays()) + ")",
                Value.duration(s.bridgeMinutes()));
        boxRow(sheet, styles, r + 4, false, true, "= Saldo de cierre", Value.signed(s.closingBalanceMinutes()));

        r += 6;
        boxRow(sheet, styles, r, true, false, "Ausencias", Value.text("Días"), Value.text("Horas"));
        boxRow(sheet, styles, r + 1, false, false, "Vacaciones", Value.text(days(s.vacationDays())),
                Value.duration(s.vacationMinutes()));
        boxRow(sheet, styles, r + 2, false, false, "Puentes a recuperar", Value.text(days(s.bridgeDays())),
                Value.duration(s.bridgeMinutes()));

        boolean exceeded = s.remotePct() > rules.maxRemotePct();
        boxRow(sheet, styles, r, true, false, "Teletrabajo", Value.text(""));
        boxRow(sheet, styles, r + 1, false, false, "En casa", Value.duration(s.remoteMinutes()));
        boxRow(sheet, styles, r + 2, false, false, "En oficina", Value.duration(s.officeMinutes()));
        boxRow(sheet, styles, r + 3, false, false, "Días en casa o mixto", Value.text(String.valueOf(s.remoteDays())));
        boxRow(sheet, styles, r + 4, false, exceeded, "Porcentaje en casa",
                new Value(Math.round(s.remotePct() * 10) / 10.0, Format.PERCENT_1, exceeded ? NEGATIVE : null));
        boxRow(sheet, styles, r + 5, false, false, "Máximo permitido",
                new Value((double) rules.maxRemotePct(), Format.PERCENT, null));

        int noteRow = r + 7;
        Row note = row(sheet, noteRow);
        note.setHeightInPoints(28);
        text(note, COL_DATE, s.workingDays() + " días laborables (" + s.normalDays() + " a jornada normal, "
                + s.intensiveDays() + " a intensiva): " + Minutes.format(s.calendarMinutes()) + " de jornada. "
                + "La diferencia y el saldo se calculan con las horas sin redondear; el redondeado (tramos de "
                + rules.roundingStepMin() + " min) es lo que se imputa. «Hasta hoy» cuenta los días ya pasados.",
                styles.footnote());
        sheet.addMergedRegion(new CellRangeAddress(noteRow, noteRow, COL_DATE, COL_NOTES));
    }

    /**
     * Fila de un bloque del resumen: con dos valores es un bloque de la izquierda (Horas, Ausencias) y con
     * uno, de la derecha (Saldo, Teletrabajo).
     */
    private static void boxRow(Sheet sheet, ExcelStyles styles, int r, boolean head, boolean bold, String label,
            Value... values) {
        Row row = row(sheet, r);
        row.setHeightInPoints(ROW_HEIGHT);
        if (values.length == 2) {
            boxLabel(sheet, styles, row, LEFT_LABEL, LEFT_LABEL_END, head, bold, label);
            boxValue(sheet, styles, row, LEFT_VALUE, LEFT_VALUE, head, bold, values[0]);
            boxValue(sheet, styles, row, LEFT_VALUE_2, LEFT_VALUE_2_END, head, bold, values[1]);
        } else {
            boxLabel(sheet, styles, row, RIGHT_LABEL, RIGHT_LABEL_END, head, bold, label);
            boxValue(sheet, styles, row, RIGHT_VALUE, RIGHT_VALUE, head, bold, values[0]);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static Fill fillOf(DaySummary day) {
        if (day.workday() != null) {
            return Fill.NONE;
        }
        if (day.dayType() == DayType.FESTIVO) {
            return Fill.HOLIDAY;
        }
        if (day.dayType() != DayType.LABORABLE) {
            return Fill.WEEKEND;
        }
        return day.absence() != null && !day.absence().halfDay() ? Fill.ABSENCE : Fill.NONE;
    }

    private static Kind kindOf(int column) {
        return switch (column) {
            case COL_DATE -> Kind.DATE;
            case COL_WEEKDAY, COL_LOCATION, COL_ABSENCE -> Kind.TEXT;
            case COL_WORKED, COL_ROUNDED -> Kind.DURATION;
            case COL_OTHER_BREAKS, COL_NOTES -> Kind.WRAP;
            default -> Kind.TIME;
        };
    }

    private static String dayLabel(DaySummary day) {
        return switch (day.dayType()) {
            case FIN_DE_SEMANA -> "Fin de semana";
            case FESTIVO -> day.holidayName() == null ? "Festivo" : "Festivo: " + day.holidayName();
            case FUERA_DE_PERIODO -> "Fuera del periodo";
            case LABORABLE -> null;
        };
    }

    private static void writeBreak(Row row, List<BreakInput> breaks, BreakType type, int startColumn) {
        breaks.stream()
                .filter(b -> b.type() == type)
                .findFirst()
                .ifPresent(b -> {
                    time(row, startColumn, b.start());
                    time(row, startColumn + 1, b.end());
                });
    }
}
