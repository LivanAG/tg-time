package com.controlhorario.importexport.excel;

import static com.controlhorario.importexport.excel.ExcelReportLayout.*;
import static com.controlhorario.importexport.excel.ExcelStyles.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import com.controlhorario.calendar.calc.PeriodRules;
import com.controlhorario.common.calc.Minutes;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Piezas comunes de las hojas exportadas: cabecera, celdas con estilo, bloques de resumen y formatos. */
final class ExcelSheets {

    static final Locale SPANISH = Locale.forLanguageTag("es-ES");

    private ExcelSheets() {
    }

    /** Valor de un bloque de resumen: número (duración, porcentaje) o texto, con su formato y color. */
    record Value(Object value, Format format, String rgb) {

        static Value duration(int minutes) {
            return new Value(minutes / (double) ExcelCells.MINUTES_PER_DAY, Format.DURATION, null);
        }

        /** "+3:20" / "-157:49" en verde o rojo: Excel no muestra duraciones negativas. */
        static Value signed(int minutes) {
            return new Value(ExcelSheets.signed(minutes), Format.TEXT, signColor(minutes));
        }

        static Value text(String text) {
            return new Value(text, Format.TEXT, null);
        }
    }

    /** Crea un libro, lo rellena y lo devuelve como .xlsx. */
    static byte[] workbook(BiConsumer<XSSFWorkbook, ExcelStyles> filler) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filler.accept(workbook, new ExcelStyles(workbook));
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Título y rejilla de datos de tres columnas (etiqueta dos columnas a la izquierda de su valor: C, G y K):
     * nombre, empresa y periodo; jornadas y teletrabajo máximo; tolerancia, comida mínima y redondeo.
     */
    static void writeHeader(Sheet sheet, ExcelStyles styles, String title, String userName, String company,
            String periodName, PeriodRules rules) {
        Row titleRow = row(sheet, TITLE_ROW);
        titleRow.setHeightInPoints(26);
        text(titleRow, COL_DATE, title, styles.plain(true, 15, NAVY));

        Row info = row(sheet, INFO_ROW);
        label(info, COL_PARAM_1 - 2, "Nombre", styles);
        text(info, COL_PARAM_1, userName, styles.param(Format.TEXT));
        label(info, COL_PARAM_2 - 2, "Empresa", styles);
        text(info, COL_PARAM_2, blankToDash(company), styles.param(Format.TEXT));
        label(info, COL_PARAM_3 - 2, "Periodo", styles);
        text(info, COL_PARAM_3, periodName, styles.param(Format.TEXT));

        Row params1 = row(sheet, PARAMS_ROW_1);
        label(params1, COL_PARAM_1 - 2, "Jornada normal", styles);
        minutes(params1, COL_PARAM_1, rules.normalDayMinutes(), styles.param(Format.TIME));
        label(params1, COL_PARAM_2 - 2, "Jornada intensiva", styles);
        minutes(params1, COL_PARAM_2, rules.intensiveDayMinutes(), styles.param(Format.TIME));
        label(params1, COL_PARAM_3 - 2, "Teletrabajo máx.", styles);
        text(params1, COL_PARAM_3, null, styles.param(Format.PERCENT)).setCellValue(rules.maxRemotePct());

        Row params2 = row(sheet, PARAMS_ROW_2);
        label(params2, COL_PARAM_1 - 2, "Tolerancia desayuno", styles);
        minutes(params2, COL_PARAM_1, rules.breakfastToleranceMin(), styles.param(Format.TIME));
        label(params2, COL_PARAM_2 - 2, "Comida mínima", styles);
        minutes(params2, COL_PARAM_2, rules.minLunchMin(), styles.param(Format.TIME));
        label(params2, COL_PARAM_3 - 2, "Redondeo", styles);
        text(params2, COL_PARAM_3, rules.roundingStepMin() + " min", styles.param(Format.TEXT));
        for (Row row : List.of(info, params1, params2)) {
            row.setHeightInPoints(16);
        }
    }

    /** A4 apaisado ajustado al ancho, márgenes estrechos y sin cuadrícula. */
    static void setUpPrint(Sheet sheet, short fitHeight) {
        sheet.setDisplayGridlines(false);
        sheet.setFitToPage(true);
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setFitWidth((short) 1);
        print.setFitHeight(fitHeight);
        sheet.setMargin(Sheet.LeftMargin, 0.4);
        sheet.setMargin(Sheet.RightMargin, 0.4);
        sheet.setMargin(Sheet.TopMargin, 0.5);
        sheet.setMargin(Sheet.BottomMargin, 0.5);
    }

    static void setColumnWidths(Sheet sheet, int[] widths) {
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    // ------------------------------------------------------------------ bloques de resumen

    /** Etiqueta de una fila de un bloque, en las columnas first..last. */
    static void boxLabel(Sheet sheet, ExcelStyles styles, Row row, int first, int last, boolean head, boolean bold,
            String label) {
        span(sheet, row, first, last, styles.box(head, true, Format.TEXT, bold, null)).setCellValue(label);
    }

    /** Valor de una fila de un bloque, en las columnas first..last. */
    static void boxValue(Sheet sheet, ExcelStyles styles, Row row, int first, int last, boolean head, boolean bold,
            Value value) {
        Cell cell = span(sheet, row, first, last, styles.box(head, false, value.format(), bold, value.rgb()));
        if (value.value() instanceof Double number) {
            cell.setCellValue(number);
        } else {
            cell.setCellValue((String) value.value());
        }
    }

    /** Celdas first..last con el mismo estilo (cada una pone sus bordes) y combinadas si son varias. */
    static Cell span(Sheet sheet, Row row, int first, int last, XSSFCellStyle style) {
        for (int c = first; c <= last; c++) {
            text(row, c, null, style);
        }
        if (last > first) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), first, last));
        }
        return row.getCell(first);
    }

    // ------------------------------------------------------------------ celdas

    static Row row(Sheet sheet, int index) {
        Row row = sheet.getRow(index);
        return row != null ? row : sheet.createRow(index);
    }

    /** Escribe un texto; con {@code style} null conserva el estilo que ya tuviera la celda. */
    static Cell text(Row row, int column, String value, XSSFCellStyle style) {
        Cell cell = row.getCell(column) != null ? row.getCell(column) : row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
        return cell;
    }

    static Cell label(Row row, int column, String text, ExcelStyles styles) {
        return text(row, column, text, styles.plain(true, 10, LABEL));
    }

    /** Duración como fracción de día (formato {@code [h]:mm}). */
    static void minutes(Row row, int column, int minutes, XSSFCellStyle style) {
        text(row, column, null, style).setCellValue(minutes / (double) ExcelCells.MINUTES_PER_DAY);
    }

    static void time(Row row, int column, LocalTime time) {
        if (time != null) {
            text(row, column, null, null).setCellValue(Minutes.of(time) / (double) ExcelCells.MINUTES_PER_DAY);
        }
    }

    // ------------------------------------------------------------------ textos

    /** "+3:20", "-1:05" o "0:00". */
    static String signed(int minutes) {
        return (minutes > 0 ? "+" : "") + Minutes.format(minutes);
    }

    /** Verde si es positivo, rojo si es negativo, sin color si es cero. */
    static String signColor(int minutes) {
        return minutes < 0 ? NEGATIVE : minutes > 0 ? POSITIVE : null;
    }

    /** "1 día", "2 días", "0,5 días". */
    static String days(double days) {
        String number = days == Math.rint(days) ? String.valueOf((long) days)
                : String.valueOf(days).replace('.', ',');
        return number + (days == 1 ? " día" : " días");
    }

    /** "Junio 2026". */
    static String monthName(YearMonth month) {
        return capitalize(month.getMonth().getDisplayName(TextStyle.FULL, SPANISH)) + " " + month.getYear();
    }

    /** "1 oct". */
    static String shortDate(LocalDate date) {
        return date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.FULL, SPANISH).substring(0, 3);
    }

    static String hhmm(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    static String blankToDash(String text) {
        return text == null || text.isBlank() ? "—" : text;
    }

    static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
