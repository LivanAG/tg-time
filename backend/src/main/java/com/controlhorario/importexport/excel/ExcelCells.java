package com.controlhorario.importexport.excel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

/**
 * Lectura de celdas usando solo los valores guardados en el fichero: en las fórmulas se lee el valor
 * cacheado ({@link Cell#getCachedFormulaResultType()}); nunca se evalúan fórmulas ni macros.
 * <p>
 * Las horas de Excel son fracciones de día (o fechas-hora con la fecha a 1899/1900) y se convierten a
 * minutos redondeando al minuto. También se aceptan textos como {@code 7:25} o {@code 0:00:00}.
 */
public final class ExcelCells {

    public static final int MINUTES_PER_DAY = 24 * 60;

    private static final Pattern CLOCK_TEXT = Pattern.compile("(-)?(\\d{1,5}):(\\d{2})(?::(\\d{2}))?");

    private ExcelCells() {
    }

    public static Cell cell(Row row, int column) {
        return row == null ? null : row.getCell(column);
    }

    public static Cell cell(Sheet sheet, String reference) {
        CellReference ref = new CellReference(reference);
        Row row = sheet.getRow(ref.getRow());
        return row == null ? null : row.getCell(ref.getCol());
    }

    /** Tipo del valor: el de la celda o, si es una fórmula, el de su resultado cacheado. */
    public static CellType type(Cell cell) {
        if (cell == null) {
            return CellType.BLANK;
        }
        CellType type = cell.getCellType();
        return type == CellType.FORMULA ? cell.getCachedFormulaResultType() : type;
    }

    public static boolean isBlank(Cell cell) {
        CellType type = type(cell);
        return type == CellType.BLANK || type == CellType._NONE
                || (type == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    /** Fecha de una celda numérica con formato de fecha; vacío si la celda no es una fecha. */
    public static Optional<LocalDate> date(Cell cell, boolean date1904) {
        if (type(cell) != CellType.NUMERIC) {
            return Optional.empty();
        }
        double value = cell.getNumericCellValue();
        if (value < 1 || !DateUtil.isValidExcelDate(value) || !DateUtil.isCellDateFormatted(cell)) {
            return Optional.empty();
        }
        LocalDateTime dateTime = DateUtil.getLocalDateTime(value, date1904);
        if (dateTime == null || dateTime.getYear() < 1901) {
            return Optional.empty();
        }
        return Optional.of(dateTime.toLocalDate());
    }

    /** Hora del día en minutos (0-1439) o null si la celda está vacía. */
    public static Integer timeOfDay(Cell cell) {
        return switch (type(cell)) {
            case BLANK, _NONE -> null;
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value < 0) {
                    throw invalid(cell, "hora no válida");
                }
                // Fecha-hora (p. ej. 1900-01-01 07:25): solo cuenta la parte de la hora.
                int minutes = (int) Math.round((value - Math.floor(value)) * MINUTES_PER_DAY);
                yield Math.min(minutes, MINUTES_PER_DAY - 1);
            }
            case STRING -> {
                String text = cell.getStringCellValue().trim();
                if (text.isEmpty()) {
                    yield null;
                }
                Integer minutes = parseClock(text);
                if (minutes == null || minutes < 0 || minutes >= MINUTES_PER_DAY) {
                    throw invalid(cell, "hora no válida («" + abbreviate(text) + "»)");
                }
                yield minutes;
            }
            default -> throw invalid(cell, "hora no válida");
        };
    }

    /**
     * Duración de un día en minutos (columnas J, L, V, AC y cabeceras F1/F2/J2/L2) o null si está vacía.
     * Un valor de un día o más se interpreta como fecha-hora y solo cuenta su parte de hora.
     */
    public static Integer durationMinutes(Cell cell) {
        return switch (type(cell)) {
            case BLANK, _NONE -> null;
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value >= 1) {
                    value = value - Math.floor(value);
                }
                yield (int) Math.round(value * MINUTES_PER_DAY);
            }
            case STRING -> parseDurationText(cell);
            default -> throw invalid(cell, "duración no válida");
        };
    }

    /** Duración larga (p. ej. las horas de convenio, 1760:00) en minutos, sin recortar a un día. */
    public static Integer totalMinutes(Cell cell) {
        return switch (type(cell)) {
            case BLANK, _NONE -> null;
            case NUMERIC -> (int) Math.round(cell.getNumericCellValue() * MINUTES_PER_DAY);
            case STRING -> parseDurationText(cell);
            default -> throw invalid(cell, "duración no válida");
        };
    }

    /** Número entero (porcentajes con formato % se pasan a 0-100) o null si la celda está vacía. */
    public static Integer integer(Cell cell) {
        return switch (type(cell)) {
            case BLANK, _NONE -> null;
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                String format = cell.getCellStyle() == null ? null : cell.getCellStyle().getDataFormatString();
                if (format != null && format.contains("%")) {
                    value = value * 100;
                }
                yield (int) Math.round(value);
            }
            case STRING -> {
                String text = cell.getStringCellValue().trim().replace("%", "").trim();
                if (text.isEmpty()) {
                    yield null;
                }
                try {
                    yield (int) Math.round(Double.parseDouble(text.replace(',', '.')));
                } catch (NumberFormatException e) {
                    throw invalid(cell, "número no válido («" + abbreviate(text) + "»)");
                }
            }
            default -> throw invalid(cell, "número no válido");
        };
    }

    /** Texto recortado, o null si la celda está vacía. Los números no se convierten a texto. */
    public static String text(Cell cell) {
        if (type(cell) != CellType.STRING) {
            if (isBlank(cell)) {
                return null;
            }
            throw invalid(cell, "se esperaba un texto");
        }
        String text = cell.getStringCellValue().trim();
        return text.isEmpty() ? null : text;
    }

    /** Referencia de la celda para los mensajes, p. ej. {@code B7}. */
    public static String reference(Cell cell) {
        return new CellReference(cell.getRowIndex(), cell.getColumnIndex()).formatAsString();
    }

    private static Integer parseDurationText(Cell cell) {
        String text = cell.getStringCellValue().trim();
        if (text.isEmpty()) {
            return null;
        }
        Integer minutes = parseClock(text);
        if (minutes == null) {
            throw invalid(cell, "duración no válida («" + abbreviate(text) + "»)");
        }
        return minutes;
    }

    /** "7:25", "07:25:40", "-0:30", "1760:00:00" → minutos (los segundos se redondean); null si no encaja. */
    static Integer parseClock(String text) {
        Matcher m = CLOCK_TEXT.matcher(text);
        if (!m.matches()) {
            return null;
        }
        int hours = Integer.parseInt(m.group(2));
        int minutes = Integer.parseInt(m.group(3));
        int seconds = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
        if (minutes >= 60 || seconds >= 60) {
            return null;
        }
        int total = hours * 60 + minutes + (seconds >= 30 ? 1 : 0);
        return m.group(1) == null ? total : -total;
    }

    private static CellReadException invalid(Cell cell, String what) {
        return new CellReadException("Celda " + reference(cell) + ": " + what);
    }

    private static String abbreviate(String text) {
        return text.length() <= 20 ? text : text.substring(0, 20) + "…";
    }
}
