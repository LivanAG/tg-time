package com.controlhorario.importexport.excel;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Diseño de la hoja mensual del Excel HORAS_IZERTIS (docs/EXCEL.md). Las filas se numeran como en
 * Excel (base 1) y las columnas con índice base 0 (A = 0).
 * <p>
 * Cinco semanas de lunes a viernes: filas 7-11, 14-18, 21-25, 28-32 y 35-39; las filas 12, 19, 26, 33
 * y 40 son los subtotales semanales. La fecha de cada fila se calcula por su posición en la rejilla:
 * la rejilla empieza el lunes de la semana del primer día laborable del mes (si el día 1 cae en fin
 * de semana, el lunes siguiente).
 */
public final class ExcelLayout {

    public static final int FIRST_DATA_ROW = 7;
    public static final int WEEKS = 5;
    public static final int DAYS_PER_WEEK = 5;
    public static final int ROWS_PER_WEEK = 7;
    public static final int TOTAL_ROW = 43;

    public static final int COL_DATE = 0;               // A
    public static final int COL_START = 1;              // B: ENTRADA
    public static final int COL_BREAKFAST_START = 2;    // C
    public static final int COL_BREAKFAST_END = 3;      // D
    public static final int COL_LUNCH_START = 4;        // E
    public static final int COL_LUNCH_END = 5;          // F
    public static final int COL_OTHER_START = 6;        // G
    public static final int COL_OTHER_END = 7;          // H
    public static final int COL_END = 8;                // I: SALIDA
    public static final int COL_LUNCH_TOTAL = 9;        // J: total comida (a mano)
    public static final int COL_BREAKFAST_DEDUCTED = 10; // K
    public static final int COL_WORKED = 11;            // L: Total Día
    public static final int COL_LOCATION = 12;          // M: O / C / M
    public static final int COL_REMOTE_START = 13;      // N
    public static final int COL_REMOTE_END = 14;        // O
    public static final int COL_ROUNDED = 15;           // P

    private static final List<Integer> DATA_ROWS;

    static {
        List<Integer> rows = new ArrayList<>(WEEKS * DAYS_PER_WEEK);
        for (int week = 0; week < WEEKS; week++) {
            for (int day = 0; day < DAYS_PER_WEEK; day++) {
                rows.add(FIRST_DATA_ROW + week * ROWS_PER_WEEK + day);
            }
        }
        DATA_ROWS = List.copyOf(rows);
    }

    private ExcelLayout() {
    }

    /** Filas de datos (base 1): 7-11, 14-18, 21-25, 28-32, 35-39. */
    public static List<Integer> dataRows() {
        return DATA_ROWS;
    }

    /** Fila del subtotal de la semana {@code week} (0-4): 12, 19, 26, 33, 40. */
    public static int subtotalRow(int week) {
        return FIRST_DATA_ROW + week * ROWS_PER_WEEK + DAYS_PER_WEEK;
    }

    /** Lunes con el que empieza la rejilla del mes. */
    public static LocalDate gridStart(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate firstWorkingDay = switch (first.getDayOfWeek()) {
            case SATURDAY -> first.plusDays(2);
            case SUNDAY -> first.plusDays(1);
            default -> first;
        };
        return firstWorkingDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Fecha que corresponde a una fila de datos (puede caer fuera del mes: esas filas se ignoran). */
    public static LocalDate dateOf(YearMonth month, int row) {
        int offset = row - FIRST_DATA_ROW;
        int week = offset / ROWS_PER_WEEK;
        int dayOfWeek = offset % ROWS_PER_WEEK;
        return gridStart(month).plusDays((long) week * 7 + dayOfWeek);
    }

    /** Fila de datos de una fecha del mes, o vacío si no tiene fila (fin de semana u otro mes). */
    public static OptionalInt rowOf(YearMonth month, LocalDate date) {
        if (!YearMonth.from(date).equals(month)) {
            return OptionalInt.empty();
        }
        long days = ChronoUnit.DAYS.between(gridStart(month), date);
        long week = Math.floorDiv(days, 7);
        long dayOfWeek = Math.floorMod(days, 7);
        if (week < 0 || week >= WEEKS || dayOfWeek >= DAYS_PER_WEEK) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(FIRST_DATA_ROW + (int) week * ROWS_PER_WEEK + (int) dayOfWeek);
    }
}
