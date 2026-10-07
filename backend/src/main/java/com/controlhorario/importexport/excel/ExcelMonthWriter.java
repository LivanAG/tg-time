package com.controlhorario.importexport.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.Minutes;
import com.controlhorario.summary.calc.DaySummary;
import com.controlhorario.summary.calc.MonthSummary;
import com.controlhorario.workday.BreakType;
import com.controlhorario.workday.Location;
import com.controlhorario.workday.calc.BreakInput;
import com.controlhorario.workday.calc.WorkdayCalculator;
import com.controlhorario.workday.calc.WorkdayInput;
import com.controlhorario.workday.calc.WorkdayResult;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Genera una hoja mensual con el diseño del Excel original (docs/EXCEL.md), de forma que se pueda
 * volver a importar: cabecera F1-F3, J1/J2 y L1/L2; filas 7-39 por la misma rejilla; columnas B-I,
 * M y N/O con los datos; J/K/L/P con los valores calculados (no fórmulas); subtotales
 * semanales en las filas 12, 19, 26, 33 y 40; pie con teóricas (C46), hechas (C47), faltan (C48),
 * sobran (C49) y vacaciones (C53).
 */
public final class ExcelMonthWriter {

    /**
     * Datos de un mes.
     *
     * @param summary  resumen del mes (MonthSummaryService): L, P, subtotales y pie
     * @param workdays fichajes del mes (también los que caen fuera del periodo del resumen)
     */
    public record MonthData(String userName, String company, PeriodRules rules, MonthSummary summary,
            Collection<WorkdayInput> workdays) {
    }

    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final String[] HEADERS = {
        "Día", "ENTRADA", "Salida Desay", "Entrada Desay", "Salida Comida", "Entrada Comida", "Hora Salida",
        "Hora Entrada", "SALIDA", "Total Comida", "Total Desayuno", "Total Día", "Ubic", "Inicio casa", "Fin casa",
        "Redondeado"
    };

    /** Estilos del libro (se crean una vez: Excel limita el número de estilos). */
    private static final class Styles {
        final CellStyle bold;
        final CellStyle header;
        final CellStyle date;
        final CellStyle time;
        final CellStyle total;
        final CellStyle totalBold;
        final CellStyle center;
        final CellStyle number;

        Styles(XSSFWorkbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            short timeFormat = workbook.createDataFormat().getFormat("h:mm");
            short totalFormat = workbook.createDataFormat().getFormat("[h]:mm");

            bold = workbook.createCellStyle();
            bold.setFont(boldFont);

            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            date = workbook.createCellStyle();
            date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            time = workbook.createCellStyle();
            time.setDataFormat(timeFormat);
            time.setAlignment(HorizontalAlignment.CENTER);

            total = workbook.createCellStyle();
            total.setDataFormat(totalFormat);
            total.setAlignment(HorizontalAlignment.CENTER);

            totalBold = workbook.createCellStyle();
            totalBold.setDataFormat(totalFormat);
            totalBold.setAlignment(HorizontalAlignment.CENTER);
            totalBold.setFont(boldFont);

            center = workbook.createCellStyle();
            center.setAlignment(HorizontalAlignment.CENTER);

            number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat("0.#"));
            number.setAlignment(HorizontalAlignment.LEFT);
        }
    }

    public byte[] write(MonthData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            YearMonth month = data.summary().month();
            Sheet sheet = workbook.createSheet(sheetName(month));
            writeHeader(sheet, styles, data, month);
            writeDays(sheet, styles, data, month);
            writeFooter(sheet, styles, data.summary());
            setColumnWidths(sheet);
            sheet.createFreezePane(1, 6);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "Junio 2026". */
    public static String sheetName(YearMonth month) {
        String name = month.getMonth().getDisplayName(TextStyle.FULL, SPANISH);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + month.getYear();
    }

    private static void writeHeader(Sheet sheet, Styles styles, MonthData data, YearMonth month) {
        PeriodRules rules = data.rules();
        MonthSummary summary = data.summary();
        Row r1 = row(sheet, 1);
        Row r2 = row(sheet, 2);
        Row r3 = row(sheet, 3);

        text(r1, 0, "Empresa:", styles.bold);
        text(r1, 1, data.company(), null);
        text(r2, 0, "Nombre:", styles.bold);
        text(r2, 1, data.userName(), null);
        text(r3, 0, "Mes:", styles.bold);
        Cell monthCell = r3.createCell(1);
        monthCell.setCellValue(month.atDay(1));
        monthCell.setCellStyle(styles.date);

        text(r1, 4, "Tiempo min comida", styles.bold);
        duration(r1, 5, rules.minLunchMin(), styles.time);
        text(r2, 4, "Tiempo desayuno", styles.bold);
        duration(r2, 5, rules.breakfastToleranceMin(), styles.time);
        text(r3, 4, "% R.Domic.", styles.bold);
        number(r3, 5, rules.maxRemotePct(), null);

        // Como en el original: J = días y horas de la jornada principal; L solo en los meses mixtos.
        boolean mixed = summary.normalDays() > 0 && summary.intensiveDays() > 0;
        boolean onlyIntensive = summary.normalDays() == 0 && summary.intensiveDays() > 0;
        text(r1, 8, "Dias Mes:", styles.bold);
        number(r1, 9, onlyIntensive ? summary.intensiveDays() : summary.normalDays(), null);
        text(r2, 8, "Horas / Día:", styles.bold);
        duration(r2, 9, onlyIntensive ? rules.intensiveDayMinutes() : rules.normalDayMinutes(), styles.time);
        if (mixed) {
            text(r1, 10, "Dias Mes:", styles.bold);
            number(r1, 11, summary.intensiveDays(), null);
            text(r2, 10, "Horas / Día:", styles.bold);
            duration(r2, 11, rules.intensiveDayMinutes(), styles.time);
        }

        text(row(sheet, 4), ExcelLayout.COL_REMOTE_START, "Teletrabajo tardes MIXTO", styles.bold);
        Row headerRow = row(sheet, 5);
        for (int c = 0; c < HEADERS.length; c++) {
            text(headerRow, c, HEADERS[c], styles.header);
        }
    }

    private static void writeDays(Sheet sheet, Styles styles, MonthData data, YearMonth month) {
        PeriodRules rules = data.rules();
        WorkdayCalculator calculator = new WorkdayCalculator(rules.breakfastToleranceMin(), rules.minLunchMin());
        Map<LocalDate, DaySummary> days = new HashMap<>();
        for (DaySummary day : data.summary().days()) {
            days.put(day.date(), day);
        }
        Map<LocalDate, WorkdayInput> workdays = new HashMap<>();
        for (WorkdayInput w : data.workdays()) {
            if (YearMonth.from(w.date()).equals(month)) {
                workdays.put(w.date(), w);
            }
        }

        for (int r : ExcelLayout.dataRows()) {
            LocalDate date = ExcelLayout.dateOf(month, r);
            if (!YearMonth.from(date).equals(month)) {
                continue;
            }
            Row row = row(sheet, r);
            Cell dateCell = row.createCell(ExcelLayout.COL_DATE);
            dateCell.setCellValue(date);
            dateCell.setCellStyle(styles.date);

            WorkdayInput workday = workdays.get(date);
            if (workday == null) {
                continue;
            }
            DaySummary day = days.get(date);
            WorkdayResult result = day != null && day.result() != null ? day.result()
                    : calculator.validate(workday).isEmpty() ? calculator.calculate(workday) : null;

            time(row, ExcelLayout.COL_START, workday.start(), styles.time);
            time(row, ExcelLayout.COL_END, workday.end(), styles.time);
            writeBreak(row, workday.breaks(), BreakType.DESAYUNO, ExcelLayout.COL_BREAKFAST_START, styles);
            writeBreak(row, workday.breaks(), BreakType.COMIDA, ExcelLayout.COL_LUNCH_START, styles);
            writeBreak(row, workday.breaks(), BreakType.OTRA, ExcelLayout.COL_OTHER_START, styles);
            if (result != null) {
                duration(row, ExcelLayout.COL_LUNCH_TOTAL, result.lunchDeductedMinutes(), styles.time);
                duration(row, ExcelLayout.COL_BREAKFAST_DEDUCTED, result.breakfastDeductedMinutes(), styles.time);
                duration(row, ExcelLayout.COL_WORKED, result.workedMinutes(), styles.time);
            }
            if (day != null && day.result() != null) {
                duration(row, ExcelLayout.COL_ROUNDED, day.roundedMinutes(), styles.time);
            }
            text(row, ExcelLayout.COL_LOCATION, locationCode(workday.location()), styles.center);
            if (workday.location() == Location.MIXTO && workday.remoteMinutes() != null && workday.remoteMinutes() > 0) {
                // Tramo en casa al final de la jornada ("teletrabajo tardes"): N = salida - minutos en casa.
                int remote = Math.min(workday.remoteMinutes(), Minutes.between(workday.start(), workday.end()));
                time(row, ExcelLayout.COL_REMOTE_START, workday.end().minusMinutes(remote), styles.time);
                time(row, ExcelLayout.COL_REMOTE_END, workday.end(), styles.time);
            }
        }

        // Subtotales por semana de la rejilla (incluye el fin de semana, por si se trabajó).
        LocalDate gridStart = ExcelLayout.gridStart(month);
        for (int week = 0; week < ExcelLayout.WEEKS; week++) {
            LocalDate from = gridStart.plusDays(7L * week);
            int worked = 0;
            int rounded = 0;
            for (int d = 0; d < 7; d++) {
                DaySummary day = days.get(from.plusDays(d));
                if (day != null) {
                    worked += day.workedMinutes();
                    rounded += day.roundedMinutes();
                }
            }
            Row row = row(sheet, ExcelLayout.subtotalRow(week));
            text(row, ExcelLayout.COL_DATE, "Semana " + (week + 1), styles.bold);
            duration(row, ExcelLayout.COL_WORKED, worked, styles.totalBold);
            duration(row, ExcelLayout.COL_ROUNDED, rounded, styles.totalBold);
        }
    }

    private static void writeFooter(Sheet sheet, Styles styles, MonthSummary summary) {
        int rounded = summary.days().stream().mapToInt(DaySummary::roundedMinutes).sum();
        Row total = row(sheet, ExcelLayout.TOTAL_ROW);
        text(total, ExcelLayout.COL_DATE, "Total Mes", styles.bold);
        duration(total, ExcelLayout.COL_WORKED, summary.workedMinutes(), styles.totalBold);
        duration(total, ExcelLayout.COL_ROUNDED, rounded, styles.totalBold);

        int theoretical = summary.theoreticalMinutes();
        int worked = summary.workedMinutes();
        Row r46 = row(sheet, 46);
        text(r46, 0, "HORAS TOTAL MES:", styles.bold);
        duration(r46, 2, theoretical, styles.total);
        Row r47 = row(sheet, 47);
        text(r47, 1, "Hechas:", styles.bold);
        duration(r47, 2, worked, styles.total);
        Row r48 = row(sheet, 48);
        text(r48, 1, "Faltan:", styles.bold);
        duration(r48, 2, Math.max(0, theoretical - worked), styles.total);
        Row r49 = row(sheet, 49);
        text(r49, 1, "Sobran:", styles.bold);
        duration(r49, 2, Math.max(0, worked - theoretical), styles.total);
        Row r53 = row(sheet, 53);
        text(r53, 0, "Vacaciones:", styles.bold);
        Cell vacations = r53.createCell(2);
        vacations.setCellValue(summary.vacationDays());
        vacations.setCellStyle(styles.number);
    }

    private static void writeBreak(Row row, List<BreakInput> breaks, BreakType type, int startColumn, Styles styles) {
        breaks.stream()
                .filter(b -> b.type() == type)
                .findFirst()
                .ifPresent(b -> {
                    time(row, startColumn, b.start(), styles.time);
                    time(row, startColumn + 1, b.end(), styles.time);
                });
    }

    private static String locationCode(Location location) {
        return switch (location) {
            case OFICINA -> "O";
            case CASA -> "C";
            case MIXTO -> "M";
        };
    }

    private static void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(ExcelLayout.COL_DATE, 12 * 256);
        for (int c = ExcelLayout.COL_START; c <= ExcelLayout.COL_ROUNDED; c++) {
            sheet.setColumnWidth(c, 10 * 256);
        }
    }

    /** Fila en numeración de Excel (base 1). */
    private static Row row(Sheet sheet, int excelRow) {
        Row row = sheet.getRow(excelRow - 1);
        return row != null ? row : sheet.createRow(excelRow - 1);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        if (value == null) {
            return;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void time(Row row, int column, LocalTime value, CellStyle style) {
        duration(row, column, Minutes.of(value), style);
    }

    /** Duraciones y horas como fracción de día, igual que Excel. */
    private static void duration(Row row, int column, int minutes, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(minutes / (double) ExcelCells.MINUTES_PER_DAY);
        cell.setCellStyle(style);
    }
}
