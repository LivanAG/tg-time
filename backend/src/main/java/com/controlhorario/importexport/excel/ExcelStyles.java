package com.controlhorario.importexport.excel;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Estilos de los Excel que exporta la app ({@link ExcelMonthWriter}, {@link ExcelPeriodWriter}): paleta,
 * fuentes y formatos, creados una vez por combinación y libro (Excel limita el número de estilos).
 */
final class ExcelStyles {

    static final String NAVY = "1F3864";
    static final String BORDER = "D0D7DE";
    static final String MUTED = "6B7280";
    static final String LABEL = "44546A";
    static final String NEGATIVE = "C00000";
    static final String POSITIVE = "2E7D32";

    /** Fondo de una fila; FUTURE no tiene fondo pero atenúa el texto, como WEEKEND. */
    enum Fill {
        NONE(null), WEEKEND("F3F4F6"), HOLIDAY("FCE8E8"), ABSENCE("E6F4EA"), SUBTOTAL("DCE6F2"), TOTAL("BDD7EE"),
        CURRENT("EEF4FB"), FUTURE(null);

        final String rgb;

        Fill(String rgb) {
            this.rgb = rgb;
        }

        boolean muted() {
            return this == WEEKEND || this == FUTURE;
        }
    }

    /**
     * Tipo de celda de una tabla: WRAP es texto en varias líneas (otras pausas y notas); RIGHT, texto o
     * número alineado a la derecha (diferencias con signo, días).
     */
    enum Kind { DATE, TEXT, CENTER, TIME, DURATION, WRAP, RIGHT }

    /** Formato de un valor de la cabecera o de un bloque de resumen. */
    enum Format { TEXT, TIME, DURATION, PERCENT, PERCENT_1 }

    private final XSSFWorkbook workbook;
    private final Map<String, XSSFCellStyle> cache = new HashMap<>();
    private final Map<String, XSSFFont> fonts = new HashMap<>();
    private final short dateFormat;
    private final short timeFormat;
    private final short durationFormat;
    private final short percentFormat;
    private final short percent1Format;

    ExcelStyles(XSSFWorkbook workbook) {
        this.workbook = workbook;
        this.dateFormat = workbook.createDataFormat().getFormat("dd/mm/yyyy");
        this.timeFormat = workbook.createDataFormat().getFormat("h:mm");
        this.durationFormat = workbook.createDataFormat().getFormat("[h]:mm");
        this.percentFormat = workbook.createDataFormat().getFormat("0\" %\"");
        this.percent1Format = workbook.createDataFormat().getFormat("0.0\" %\"");
    }

    XSSFFont font(boolean bold, int points, String rgb) {
        return font(bold, points, rgb, false);
    }

    private XSSFFont font(boolean bold, int points, String rgb, boolean underline) {
        return fonts.computeIfAbsent(bold + "|" + points + "|" + rgb + "|" + underline, k -> {
            XSSFFont font = workbook.createFont();
            font.setFontName("Calibri");
            font.setFontHeightInPoints((short) points);
            font.setBold(bold);
            if (rgb != null) {
                font.setColor(color(rgb));
            }
            if (underline) {
                font.setUnderline(FontUnderline.SINGLE);
            }
            return font;
        });
    }

    /** Celda de una tabla (días del mes, meses del periodo). */
    XSSFCellStyle cell(Kind kind, Fill fill, boolean bold) {
        return cell(kind, fill, bold, null);
    }

    /** Igual, con color de texto ({@code rgb} null: el de la fila). */
    XSSFCellStyle cell(Kind kind, Fill fill, boolean bold, String rgb) {
        return cache.computeIfAbsent("cell|" + kind + "|" + fill + "|" + bold + "|" + rgb, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            String fontColor = rgb != null ? rgb : fill.muted() ? MUTED : null;
            style.setFont(font(bold, 10, fontColor));
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            borders(style, BORDER);
            if (fill.rgb != null) {
                style.setFillForegroundColor(color(fill.rgb));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            switch (kind) {
                case DATE -> {
                    style.setDataFormat(dateFormat);
                    style.setAlignment(HorizontalAlignment.LEFT);
                }
                case TIME -> {
                    style.setDataFormat(timeFormat);
                    style.setAlignment(HorizontalAlignment.CENTER);
                }
                case DURATION -> {
                    style.setDataFormat(durationFormat);
                    style.setAlignment(HorizontalAlignment.RIGHT);
                    style.setIndention((short) 1);
                }
                case CENTER -> style.setAlignment(HorizontalAlignment.CENTER);
                case WRAP -> {
                    style.setWrapText(true);
                    style.setAlignment(HorizontalAlignment.LEFT);
                    style.setIndention((short) 1);
                }
                case RIGHT -> {
                    style.setAlignment(HorizontalAlignment.RIGHT);
                    style.setIndention((short) 1);
                }
                case TEXT -> style.setAlignment(HorizontalAlignment.LEFT);
            }
            return style;
        });
    }

    /** Enlace a otra hoja dentro de una celda de tabla: azul marino y subrayado. */
    XSSFCellStyle link(Fill fill, boolean bold) {
        return cache.computeIfAbsent("link|" + fill + "|" + bold, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(cell(Kind.TEXT, fill, bold));
            style.setFont(font(bold, 10, NAVY, true));
            return style;
        });
    }

    /** Cabecera de una tabla: blanco sobre azul marino. */
    XSSFCellStyle header() {
        return cache.computeIfAbsent("header", k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font(true, 10, "FFFFFF"));
            style.setFillForegroundColor(color(NAVY));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            borders(style, "FFFFFF");
            return style;
        });
    }

    XSSFCellStyle plain(boolean bold, int points, String rgb) {
        return cache.computeIfAbsent("plain|" + bold + "|" + points + "|" + rgb, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font(bold, points, rgb));
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        });
    }

    /** Valor de la cabecera (datos y parámetros), alineado a la izquierda junto a su etiqueta. */
    XSSFCellStyle param(Format format) {
        return cache.computeIfAbsent("param|" + format, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font(false, 10, null));
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setAlignment(HorizontalAlignment.LEFT);
            format(style, format);
            return style;
        });
    }

    /**
     * Celda de un bloque de resumen: con {@code head} es la fila de títulos (fondo azul claro); las
     * etiquetas van a la izquierda y los valores a la derecha.
     */
    XSSFCellStyle box(boolean head, boolean label, Format format, boolean bold, String rgb) {
        return cache.computeIfAbsent("box|" + head + "|" + label + "|" + format + "|" + bold + "|" + rgb, k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            String fontColor = head ? NAVY : label && !bold ? LABEL : rgb;
            style.setFont(font(bold || head, 10, fontColor));
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setAlignment(label ? HorizontalAlignment.LEFT : HorizontalAlignment.RIGHT);
            borders(style, BORDER);
            if (head) {
                style.setFillForegroundColor(color(Fill.SUBTOTAL.rgb));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            format(style, format);
            return style;
        });
    }

    /** Nota al pie: pequeña, gris y en varias líneas. */
    XSSFCellStyle footnote() {
        return cache.computeIfAbsent("footnote", k -> {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font(false, 9, MUTED));
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setWrapText(true);
            return style;
        });
    }

    private void format(XSSFCellStyle style, Format format) {
        switch (format) {
            case TIME -> style.setDataFormat(timeFormat);
            case DURATION -> style.setDataFormat(durationFormat);
            case PERCENT -> style.setDataFormat(percentFormat);
            case PERCENT_1 -> style.setDataFormat(percent1Format);
            case TEXT -> { }
        }
    }

    private static void borders(XSSFCellStyle style, String rgb) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor border = color(rgb);
        style.setTopBorderColor(border);
        style.setBottomBorderColor(border);
        style.setLeftBorderColor(border);
        style.setRightBorderColor(border);
    }

    private static XSSFColor color(String rgb) {
        return new XSSFColor(new byte[] {
            (byte) Integer.parseInt(rgb.substring(0, 2), 16),
            (byte) Integer.parseInt(rgb.substring(2, 4), 16),
            (byte) Integer.parseInt(rgb.substring(4, 6), 16)}, null);
    }
}
