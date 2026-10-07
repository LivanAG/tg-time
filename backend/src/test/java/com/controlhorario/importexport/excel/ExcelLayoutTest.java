package com.controlhorario.importexport.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.OptionalInt;

import com.controlhorario.calendar.calc.PeriodCalendar;

import org.junit.jupiter.api.Test;

class ExcelLayoutTest {

    @Test
    void dataAndSubtotalRowsFollowTheTemplate() {
        assertThat(ExcelLayout.dataRows()).containsExactly(7, 8, 9, 10, 11, 14, 15, 16, 17, 18, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 35, 36, 37, 38, 39);
        assertThat(ExcelLayout.subtotalRow(0)).isEqualTo(12);
        assertThat(ExcelLayout.subtotalRow(4)).isEqualTo(40);
    }

    @Test
    void gridStartsOnTheMondayOfTheFirstWorkingDay() {
        // Mayo 2026: el día 1 es viernes → la rejilla empieza el lunes 27/04 y el 1 está en la fila 11.
        assertThat(ExcelLayout.gridStart(YearMonth.of(2026, 5))).isEqualTo(LocalDate.of(2026, 4, 27));
        assertThat(ExcelLayout.dateOf(YearMonth.of(2026, 5), 11)).isEqualTo(LocalDate.of(2026, 5, 1));
        // Agosto 2026: el día 1 es sábado → empieza el lunes 3.
        assertThat(ExcelLayout.gridStart(YearMonth.of(2026, 8))).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(ExcelLayout.dateOf(YearMonth.of(2026, 8), 7)).isEqualTo(LocalDate.of(2026, 8, 3));
        // Noviembre 2026: el día 1 es domingo → empieza el lunes 2.
        assertThat(ExcelLayout.gridStart(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void rowsWithWrongDatesInTheRealExcelGetTheirPositionalDate() {
        // En "Mayo 26" las filas 21-25 dicen 11-15/06; por posición son 11-15/05.
        assertThat(ExcelLayout.dateOf(YearMonth.of(2026, 5), 21)).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(ExcelLayout.dateOf(YearMonth.of(2026, 5), 25)).isEqualTo(LocalDate.of(2026, 5, 15));
        // En "Agosto" las filas 21-25 dicen 2025; por posición son 17-21/08/2026.
        assertThat(ExcelLayout.dateOf(YearMonth.of(2026, 8), 21)).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    void everyWeekdayOfEveryMonthHasExactlyOneRow() {
        for (YearMonth m = YearMonth.of(2025, 1); m.isBefore(YearMonth.of(2029, 1)); m = m.plusMonths(1)) {
            YearMonth month = m;
            for (LocalDate date : PeriodCalendar.datesOf(month)) {
                OptionalInt row = ExcelLayout.rowOf(month, date);
                boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
                assertThat(row.isPresent()).as(date.toString()).isEqualTo(!weekend);
                row.ifPresent(r -> assertThat(ExcelLayout.dateOf(month, r)).isEqualTo(date));
            }
        }
    }

    @Test
    void datesOfOtherMonthsHaveNoRow() {
        assertThat(ExcelLayout.rowOf(YearMonth.of(2026, 6), LocalDate.of(2026, 7, 1))).isEmpty();
        assertThat(ExcelLayout.rowOf(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 6))).isEmpty();
        assertThat(ExcelLayout.rowOf(YearMonth.of(2026, 6), LocalDate.of(2026, 6, 30))).hasValue(36);
    }
}
